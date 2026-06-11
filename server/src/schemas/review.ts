import { z } from "zod";

/**
 * Отзывы (ТЗ §6 FR-B08). Отзыв оставляет покупатель с доставленным/завершённым
 * заказом, содержащим товар (проверка по orderId). Один отзыв на товар от
 * пользователя; агрегаты rating/reviewCount товара пересчитывает сервер.
 */
export const reviewCreateSchema = z.object({
  rating: z.number().int().min(1).max(5),
  text: z.string().max(2000).optional(),
  /** Фото к отзыву (Storage URL); до 6. */
  photos: z.array(z.string().url().max(1024)).max(6).default([]),
  /** Заказ, подтверждающий покупку (статус DELIVERED/COMPLETED, содержит товар). */
  orderId: z.string().min(1).max(64),
});
export type ReviewCreate = z.infer<typeof reviewCreateSchema>;

export const reviewsListQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(50).default(20),
});

/**
 * Инкрементальный пересчёт среднего рейтинга (pure — под unit-тестом).
 * rating округляется до 0.1; reviewCount не может стать отрицательным.
 */
export function recomputeRating(
  prevSum: number,
  prevCount: number,
  delta: { addSum?: number; addCount?: number },
): { ratingSum: number; reviewCount: number; rating: number } {
  const ratingSum = Math.max(0, prevSum + (delta.addSum ?? 0));
  const reviewCount = Math.max(0, prevCount + (delta.addCount ?? 0));
  const rating = reviewCount > 0 ? Math.round((ratingSum / reviewCount) * 10) / 10 : 0;
  return { ratingSum, reviewCount, rating };
}
