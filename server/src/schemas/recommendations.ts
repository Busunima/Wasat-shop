import { z } from "zod";

/**
 * Рекомендации (ТЗ §6 FR-B12, MVP-эвристика без ML): «похожие товары» (related) и
 * «популярное» (popular). Чистая логика ранжирования — в services/recommendations.
 */
export const recommendationsQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(20).default(8),
  /** Фильтр популярного по категории (для секции каталога). */
  category: z.string().max(80).optional(),
});
export type RecommendationsQuery = z.infer<typeof recommendationsQuerySchema>;
