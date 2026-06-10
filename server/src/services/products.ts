import { randomUUID } from "node:crypto";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import { ApiError } from "../middleware/errorHandler.js";
import {
  computeTotalStock,
  decodeCursor,
  encodeCursor,
  type ProductCreate,
  type ProductListQuery,
  type ProductSort,
  type ProductUpdate,
} from "../schemas/product.js";
import { logger } from "../lib/logger.js";

/**
 * CRUD товаров (FR-A02, docs/data-model.md → products/{pid}).
 * Производные поля (totalStock; rating/reviewCount — Фаза 3) пишет только сервер.
 * Ответы API не содержат Firestore-типов (Timestamp) — поля мапятся явно.
 */

export interface ApiProduct {
  id: string;
  name: string;
  description: string;
  price: number;
  originalPrice: number | null;
  images: string[];
  category: string | null;
  tags: string[];
  variants: Array<{ size?: string; color?: string; stock: number; sku?: string }>;
  totalStock: number;
  status: string;
  sku: string | null;
  barcode: string | null;
}

function productsCol(storeId: string) {
  return db().collection("stores").doc(storeId).collection("products");
}

function toApiProduct(data: FirebaseFirestore.DocumentData): ApiProduct {
  return {
    id: data["id"] as string,
    name: data["name"] as string,
    description: (data["description"] as string) ?? "",
    price: data["price"] as number,
    originalPrice: (data["originalPrice"] as number | undefined) ?? null,
    images: (data["images"] as string[]) ?? [],
    category: (data["category"] as string | undefined) ?? null,
    tags: (data["tags"] as string[]) ?? [],
    variants: (data["variants"] as ApiProduct["variants"]) ?? [],
    totalStock: (data["totalStock"] as number) ?? 0,
    status: data["status"] as string,
    sku: (data["sku"] as string | undefined) ?? null,
    barcode: (data["barcode"] as string | undefined) ?? null,
  };
}

async function assertStoreExists(storeId: string): Promise<void> {
  const snap = await db().collection("stores").doc(storeId).get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Магазин не найден");
}

export async function createProduct(storeId: string, input: ProductCreate): Promise<ApiProduct> {
  await assertStoreExists(storeId);

  const productId = randomUUID();
  const docData = {
    id: productId,
    name: input.name,
    description: input.description ?? "",
    price: input.price,
    originalPrice: input.originalPrice ?? null,
    images: input.images,
    category: input.category ?? null,
    tags: input.tags,
    variants: input.variants,
    totalStock: computeTotalStock(input.variants),
    rating: 0,
    reviewCount: 0,
    status: input.status,
    sku: input.sku ?? null,
    barcode: input.barcode ?? null,
    createdAt: FieldValue.serverTimestamp(),
  };
  await productsCol(storeId).doc(productId).set(docData);

  logger.info("Товар создан", { storeId, productId });
  return toApiProduct(docData);
}

export async function updateProduct(
  storeId: string,
  productId: string,
  input: ProductUpdate,
): Promise<ApiProduct> {
  const ref = productsCol(storeId).doc(productId);

  await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new ApiError("NOT_FOUND", "Товар не найден");

    // undefined = поле не передано (PATCH не трогает); null = явная очистка.
    const patch: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(input)) {
      if (value !== undefined) patch[key] = value;
    }
    // totalStock — производное: пересчитываем при изменении вариантов.
    if (input.variants !== undefined) {
      patch["totalStock"] = computeTotalStock(input.variants);
    }
    tx.update(ref, patch);
  });

  const updated = await ref.get();
  return toApiProduct(updated.data()!);
}

export async function deleteProduct(storeId: string, productId: string): Promise<void> {
  const ref = productsCol(storeId).doc(productId);
  const snap = await ref.get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Товар не найден");
  await ref.delete();
  logger.info("Товар удалён", { storeId, productId });
}

export async function getProduct(
  storeId: string,
  productId: string,
  includeDrafts: boolean,
): Promise<ApiProduct> {
  const snap = await productsCol(storeId).doc(productId).get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Товар не найден");
  const product = toApiProduct(snap.data()!);
  if (product.status !== "active" && !includeDrafts) {
    throw new ApiError("NOT_FOUND", "Товар не найден");
  }
  return product;
}

export interface ProductPage {
  items: ApiProduct[];
  /** null — страниц больше нет. */
  nextCursor: string | null;
}

const SORT_FIELD: Record<ProductSort, string> = {
  new: "createdAt",
  price_asc: "price",
  price_desc: "price",
  rating: "rating",
};

function sortDirection(sort: ProductSort): FirebaseFirestore.OrderByDirection {
  return sort === "price_asc" ? "asc" : "desc";
}

/** Значение поля сортировки документа → число для курсора (Timestamp → ms). */
function sortValueOf(data: FirebaseFirestore.DocumentData, sort: ProductSort): number {
  const raw = data[SORT_FIELD[sort]] as number | Timestamp | undefined;
  if (raw instanceof Timestamp) return raw.toMillis();
  return raw ?? 0;
}

/**
 * Листинг каталога (FR-B02): равенства (status/category) и сортировка — в Firestore
 * (композитные индексы в firebase/firestore.indexes.json); поиск q, диапазон цены и
 * «в наличии» — постфильтр в цикле добора страницы (временно до Algolia, ТЗ §2/§15;
 * масштаб MVP ≤10k товаров). Курсор — значение сортировки + id последнего
 * ВОЗВРАЩЁННОГО элемента: отфильтрованные документы при докрутке просто
 * перефильтровываются, дубликатов не возникает.
 */
export async function listProducts(
  storeId: string,
  includeDrafts: boolean,
  query: ProductListQuery,
): Promise<ProductPage> {
  await assertStoreExists(storeId);

  const { sort, limit } = query;
  const direction = sortDirection(sort);
  const qLower = query.q?.trim().toLowerCase();

  let base: FirebaseFirestore.Query = productsCol(storeId);
  if (!includeDrafts) base = base.where("status", "==", "active");
  if (query.category) base = base.where("category", "==", query.category);
  base = base.orderBy(SORT_FIELD[sort], direction).orderBy("id", direction);

  const cursor = query.cursor ? decodeCursor(query.cursor) : null;
  if (cursor) {
    const value =
      sort === "new" ? Timestamp.fromMillis(cursor.v) : cursor.v;
    base = base.startAfter(value, cursor.id);
  }

  const matches = (p: ApiProduct): boolean =>
    (!qLower || p.name.toLowerCase().includes(qLower)) &&
    (!query.inStock || p.totalStock > 0) &&
    (query.minPrice === undefined || p.price >= query.minPrice) &&
    (query.maxPrice === undefined || p.price <= query.maxPrice);

  const items: ApiProduct[] = [];
  let lastReturned: { v: number; id: string } | null = null;
  let exhausted = false;
  let pageQuery = base;

  // Добор страницы: батчи по 50, пока не наберём limit или коллекция не кончится.
  while (items.length < limit && !exhausted) {
    const snap = await pageQuery.limit(50).get();
    exhausted = snap.size < 50;
    for (const doc of snap.docs) {
      const product = toApiProduct(doc.data());
      if (!matches(product)) continue;
      items.push(product);
      lastReturned = { v: sortValueOf(doc.data(), sort), id: product.id };
      if (items.length >= limit) break;
    }
    if (!exhausted && items.length < limit) {
      const lastDoc = snap.docs[snap.size - 1]!;
      pageQuery = base.startAfter(lastDoc);
    }
  }

  return {
    items,
    nextCursor: items.length >= limit && lastReturned ? encodeCursor(lastReturned) : null,
  };
}
