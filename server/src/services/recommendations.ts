import { db } from "../lib/firebase.js";
import { getAnalytics } from "./analytics.js";
import { getProduct, listProducts, type ApiProduct } from "./products.js";

/**
 * Рекомендации (FR-B12, MVP-эвристика). «Похожие» — товары той же категории,
 * ранжированные по совпадению тегов и рейтингу; «популярное» — топ по просмотрам
 * за 30 дней (FR-A05) с откатом на рейтинг. Без ML и внешних сервисов; всё чтение —
 * поверх существующих getProduct/listProducts/getAnalytics.
 */

/** Признак наличия (в продаже) — товар без вариантов с нулём всё ещё может быть активен. */
function inStock(p: ApiProduct): boolean {
  return p.totalStock > 0;
}

/**
 * Чистое ранжирование «похожих» относительно текущего товара. Исключает сам товар;
 * сортирует по числу общих тегов (desc), затем по rating (desc), затем «в наличии»
 * выше; срез до limit. Детерминирована — под unit-тестом.
 */
export function rankRelated(
  current: { id: string; tags: string[]; rating?: number },
  candidates: Array<ApiProduct & { rating?: number }>,
  limit: number,
): ApiProduct[] {
  const currentTags = new Set(current.tags ?? []);
  const sharedTags = (p: ApiProduct): number =>
    (p.tags ?? []).reduce((n, t) => (currentTags.has(t) ? n + 1 : n), 0);

  return candidates
    .filter((p) => p.id !== current.id)
    .map((p) => ({ p, shared: sharedTags(p), rating: p.rating ?? 0, stock: inStock(p) ? 1 : 0 }))
    .sort(
      (a, b) =>
        b.shared - a.shared || b.rating - a.rating || b.stock - a.stock || a.p.id.localeCompare(b.p.id),
    )
    .slice(0, limit)
    .map((x) => x.p);
}

/** rating документа товара (производное поле, не входит в ApiProduct). */
async function withRating(storeId: string, products: ApiProduct[]): Promise<Array<ApiProduct & { rating: number }>> {
  if (products.length === 0) return [];
  const refs = products.map((p) => db().collection("stores").doc(storeId).collection("products").doc(p.id));
  const snaps = await db().getAll(...refs);
  const ratingById = new Map<string, number>();
  for (const snap of snaps) ratingById.set(snap.id, (snap.data()?.["rating"] as number) ?? 0);
  return products.map((p) => ({ ...p, rating: ratingById.get(p.id) ?? 0 }));
}

/**
 * Популярное (FR-B12): топ по просмотрам за 30 дней; при отсутствии аналитики —
 * откат на сортировку по рейтингу. Только активные и (если задано) в категории.
 */
export async function getPopular(
  storeId: string,
  limit: number,
  category?: string,
): Promise<ApiProduct[]> {
  const report = await getAnalytics(storeId, {});
  const topIds = report.topProducts.map((t) => t.productId);

  if (topIds.length > 0) {
    const refs = topIds.map((id) => db().collection("stores").doc(storeId).collection("products").doc(id));
    const snaps = await db().getAll(...refs);
    const byId = new Map(snaps.filter((s) => s.exists).map((s) => [s.id, s.data()!]));
    const ordered: ApiProduct[] = [];
    for (const id of topIds) {
      const data = byId.get(id);
      if (!data) continue;
      if (data["status"] !== "active") continue;
      if (category && data["category"] !== category) continue;
      ordered.push(toApi(data));
      if (ordered.length >= limit) break;
    }
    if (ordered.length > 0) return ordered;
  }

  // Откат: лучшие по рейтингу (активные, в наличии).
  const page = await listProducts(storeId, false, {
    sort: "rating",
    limit,
    inStock: true,
    ...(category ? { category } : {}),
  } as Parameters<typeof listProducts>[2]);
  return page.items;
}

/** Минимальная проекция документа товара в ApiProduct (как в services/products). */
function toApi(data: FirebaseFirestore.DocumentData): ApiProduct {
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

/**
 * Похожие товары (FR-B12): та же категория, ранжирование по тегам/рейтингу; добор
 * из «популярного» до limit. Нет категории у товара — сразу популярное.
 */
export async function getRelated(
  storeId: string,
  productId: string,
  limit: number,
): Promise<ApiProduct[]> {
  const current = await getProduct(storeId, productId, false);

  let related: ApiProduct[] = [];
  if (current.category) {
    const page = await listProducts(storeId, false, {
      sort: "rating",
      limit: limit * 3,
      category: current.category,
    } as Parameters<typeof listProducts>[2]);
    const candidates = await withRating(storeId, page.items);
    related = rankRelated({ id: current.id, tags: current.tags }, candidates, limit);
  }

  if (related.length < limit) {
    const seen = new Set([current.id, ...related.map((p) => p.id)]);
    const popular = await getPopular(storeId, limit, current.category ?? undefined);
    for (const p of popular) {
      if (seen.has(p.id)) continue;
      related.push(p);
      seen.add(p.id);
      if (related.length >= limit) break;
    }
  }

  return related.slice(0, limit);
}
