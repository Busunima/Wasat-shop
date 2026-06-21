import { createHash } from "node:crypto";
import { Timestamp, FieldValue } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import { ApiError } from "../middleware/errorHandler.js";
import { recomputeRating, type ReviewCreate } from "../schemas/review.js";
import { logger } from "../lib/logger.js";

/**
 * Отзывы (ТЗ §6 FR-B08). Право на отзыв подтверждается заказом покупателя в
 * статусе DELIVERED/COMPLETED, содержащим товар. Один отзыв на (товар, пользователь)
 * — детерминированный id. Агрегаты товара (ratingSum/reviewCount/rating)
 * пересчитываются в той же транзакции; чтение списка — публичное.
 */

const ELIGIBLE_ORDER_STATUSES = ["DELIVERED", "COMPLETED"];

export interface ApiReview {
  id: string;
  productId: string;
  customerUid: string;
  rating: number;
  text: string | null;
  photos: string[];
  orderId: string;
  createdAt: number | null;
}

function reviewsCol(storeId: string) {
  return db().collection("stores").doc(storeId).collection("reviews");
}

function productRef(storeId: string, productId: string) {
  return db().collection("stores").doc(storeId).collection("products").doc(productId);
}

function toApiReview(data: FirebaseFirestore.DocumentData): ApiReview {
  const createdAt = data["createdAt"];
  return {
    id: data["id"] as string,
    productId: data["productId"] as string,
    customerUid: data["customerUid"] as string,
    rating: data["rating"] as number,
    text: (data["text"] as string | null) ?? null,
    photos: (data["photos"] as string[]) ?? [],
    orderId: (data["orderId"] as string) ?? "",
    createdAt: createdAt instanceof Timestamp ? createdAt.toMillis() : null,
  };
}

/** Детерминированный id отзыва: один на (товар, пользователь). */
export function reviewIdFor(uid: string, productId: string): string {
  return createHash("sha256").update(`${uid}:${productId}`).digest("hex").slice(0, 32);
}

/**
 * Создание/обновление отзыва (FR-B08). Транзакция: проверка заказа (свой,
 * eligible-статус, содержит товар) → запись отзыва → пересчёт агрегатов товара.
 * Повторный отзыв тем же пользователем на товар — обновляет (заменяет рейтинг).
 */
export async function createReview(
  storeId: string,
  productId: string,
  uid: string,
  input: ReviewCreate,
): Promise<ApiReview> {
  const reviewId = reviewIdFor(uid, productId);
  const reviewRef = reviewsCol(storeId).doc(reviewId);
  const orderRef = db()
    .collection("stores").doc(storeId).collection("orders").doc(input.orderId);
  const prodRef = productRef(storeId, productId);

  const data = await db().runTransaction(async (tx) => {
    const [orderSnap, prodSnap, existingSnap] = await Promise.all([
      tx.get(orderRef),
      tx.get(prodRef),
      tx.get(reviewRef),
    ]);

    if (!prodSnap.exists) throw new ApiError("NOT_FOUND", "Товар не найден");

    const order = orderSnap.data();
    if (!order || order["customerUid"] !== uid) {
      throw new ApiError("FORBIDDEN", "Заказ не найден или не ваш");
    }
    if (!ELIGIBLE_ORDER_STATUSES.includes(order["status"] as string)) {
      throw new ApiError("FORBIDDEN", "Отзыв доступен после получения заказа");
    }
    const items = (order["items"] as Array<{ productId: string }>) ?? [];
    if (!items.some((it) => it.productId === productId)) {
      throw new ApiError("VALIDATION_ERROR", "Заказ не содержит этот товар");
    }

    const prev = prodSnap.data()!;
    const prevSum = (prev["ratingSum"] as number) ?? 0;
    const prevCount = (prev["reviewCount"] as number) ?? 0;
    const existing = existingSnap.data();

    // Обновление существующего отзыва: счётчик не растёт, сумма корректируется на дельту.
    const addCount = existing ? 0 : 1;
    const addSum = input.rating - ((existing?.["rating"] as number) ?? 0);
    const agg = recomputeRating(prevSum, prevCount, { addSum, addCount });

    const reviewData = {
      id: reviewId,
      productId,
      customerUid: uid,
      rating: input.rating,
      text: input.text ?? null,
      photos: input.photos,
      orderId: input.orderId,
      createdAt: existing?.["createdAt"] ?? FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    };
    tx.set(reviewRef, reviewData);
    tx.update(prodRef, {
      ratingSum: agg.ratingSum,
      reviewCount: agg.reviewCount,
      rating: agg.rating,
    });
    return reviewData;
  });

  logger.info("Отзыв сохранён", { storeId, productId, rating: input.rating });
  return toApiReview(data);
}

export interface ReviewPage {
  items: ApiReview[];
  nextCursor: string | null;
}

/**
 * Публичный список отзывов о товаре (FR-B08/FR-B03), новые сверху, с курсорной
 * пагинацией. Сортировка по createdAt на стороне Firestore (требует композитный
 * индекс productId↑ + createdAt↓), курсор — createdAt последнего отзыва (мс).
 */
export async function listReviews(
  storeId: string,
  productId: string,
  limit: number,
  cursor?: string,
): Promise<ReviewPage> {
  let query = reviewsCol(storeId)
    .where("productId", "==", productId)
    .orderBy("createdAt", "desc")
    .limit(limit + 1); // +1 — чтобы определить наличие следующей страницы
  const cursorMillis = cursor ? Number(cursor) : NaN;
  if (Number.isFinite(cursorMillis)) {
    query = query.startAfter(Timestamp.fromMillis(cursorMillis));
  }
  const snap = await query.get();
  const docs = snap.docs.slice(0, limit);
  const items = docs.map((doc) => toApiReview(doc.data()));
  const hasMore = snap.docs.length > limit;
  const last = items[items.length - 1];
  const nextCursor = hasMore && last?.createdAt != null ? String(last.createdAt) : null;
  return { items, nextCursor };
}

/** Удаление отзыва (модерация владельцем/сотрудником) + пересчёт агрегатов. */
export async function deleteReview(
  storeId: string,
  reviewId: string,
): Promise<void> {
  const reviewRef = reviewsCol(storeId).doc(reviewId);

  await db().runTransaction(async (tx) => {
    const snap = await tx.get(reviewRef);
    if (!snap.exists) throw new ApiError("NOT_FOUND", "Отзыв не найден");
    const review = snap.data()!;
    const prodRef = productRef(storeId, review["productId"] as string);
    const prodSnap = await tx.get(prodRef);

    if (prodSnap.exists) {
      const prev = prodSnap.data()!;
      const agg = recomputeRating(
        (prev["ratingSum"] as number) ?? 0,
        (prev["reviewCount"] as number) ?? 0,
        { addSum: -(review["rating"] as number), addCount: -1 },
      );
      tx.update(prodRef, {
        ratingSum: agg.ratingSum,
        reviewCount: agg.reviewCount,
        rating: agg.rating,
      });
    }
    tx.delete(reviewRef);
  });

  logger.info("Отзыв удалён (модерация)", { storeId, reviewId });
}
