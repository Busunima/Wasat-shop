import { randomUUID } from "node:crypto";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import { ApiError } from "../middleware/errorHandler.js";
import type { CategoryCreate, CategoryUpdate } from "../schemas/category.js";
import { logger } from "../lib/logger.js";

/**
 * CRUD категорий магазина (FR-A01). Дерево через parentId; защита от циклов
 * (обход предков) и уникальность slug в пределах магазина. Категории пишет
 * только владелец (роут), чтение — публичное (витрина строит фильтр).
 */

export interface ApiCategory {
  id: string;
  name: string;
  slug: string;
  parentId: string | null;
  order: number;
  imageUrl: string | null;
}

function categoriesCol(storeId: string) {
  return db().collection("stores").doc(storeId).collection("categories");
}

function toApiCategory(data: FirebaseFirestore.DocumentData): ApiCategory {
  return {
    id: data["id"] as string,
    name: data["name"] as string,
    slug: data["slug"] as string,
    parentId: (data["parentId"] as string | undefined) ?? null,
    order: (data["order"] as number | undefined) ?? 0,
    imageUrl: (data["imageUrl"] as string | undefined) ?? null,
  };
}

async function assertStoreExists(storeId: string): Promise<void> {
  const snap = await db().collection("stores").doc(storeId).get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Магазин не найден");
}

/** slug уникален в пределах магазина (исключая обновляемую категорию). */
async function assertSlugUnique(storeId: string, slug: string, exceptId?: string): Promise<void> {
  const snap = await categoriesCol(storeId).where("slug", "==", slug).get();
  if (snap.docs.some((d) => d.id !== exceptId)) {
    throw new ApiError("CONFLICT", "Категория с таким slug уже существует");
  }
}

/** Родитель существует и не образует цикл (обход вверх по дереву). */
async function assertParent(
  storeId: string,
  parentId: string | null,
  selfId?: string,
): Promise<void> {
  if (!parentId) return;
  if (parentId === selfId) {
    throw new ApiError("VALIDATION_ERROR", "Категория не может быть своим родителем");
  }
  let current: string | null = parentId;
  const seen = new Set<string>();
  while (current) {
    if (current === selfId) throw new ApiError("CONFLICT", "Цикл в дереве категорий");
    if (seen.has(current)) break;
    seen.add(current);
    const snap = await categoriesCol(storeId).doc(current).get();
    if (!snap.exists) throw new ApiError("NOT_FOUND", "Родительская категория не найдена");
    current = (snap.data()?.["parentId"] as string | null) ?? null;
  }
}

/** Категории магазина (FR-A01), отсортированы по order, затем имени. */
export async function listCategories(storeId: string): Promise<ApiCategory[]> {
  const snap = await categoriesCol(storeId).get();
  return snap.docs
    .map((d) => toApiCategory(d.data()))
    .sort((a, b) => a.order - b.order || a.name.localeCompare(b.name));
}

export async function createCategory(
  storeId: string,
  input: CategoryCreate,
): Promise<ApiCategory> {
  await assertStoreExists(storeId);
  await assertSlugUnique(storeId, input.slug);
  await assertParent(storeId, input.parentId);

  const id = randomUUID();
  const doc = {
    id,
    name: input.name,
    slug: input.slug,
    parentId: input.parentId,
    order: input.order,
    imageUrl: input.imageUrl ?? null,
    createdAt: FieldValue.serverTimestamp(),
  };
  await categoriesCol(storeId).doc(id).set(doc);
  logger.info("Категория создана", { storeId, id, slug: input.slug });
  return toApiCategory(doc);
}

export async function updateCategory(
  storeId: string,
  cid: string,
  input: CategoryUpdate,
): Promise<ApiCategory> {
  const ref = categoriesCol(storeId).doc(cid);
  if (!(await ref.get()).exists) throw new ApiError("NOT_FOUND", "Категория не найдена");

  if (input.slug !== undefined) await assertSlugUnique(storeId, input.slug, cid);
  if (input.parentId !== undefined) await assertParent(storeId, input.parentId, cid);

  const patch: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(input)) {
    if (value !== undefined) patch[key] = value;
  }
  if (Object.keys(patch).length > 0) await ref.update(patch);
  return toApiCategory((await ref.get()).data()!);
}

export async function deleteCategory(storeId: string, cid: string): Promise<void> {
  const ref = categoriesCol(storeId).doc(cid);
  if (!(await ref.get()).exists) throw new ApiError("NOT_FOUND", "Категория не найдена");

  const children = await categoriesCol(storeId).where("parentId", "==", cid).limit(1).get();
  if (!children.empty) {
    throw new ApiError("CONFLICT", "Сначала удалите или перенесите подкатегории");
  }
  await ref.delete();
  logger.info("Категория удалена", { storeId, id: cid });
}
