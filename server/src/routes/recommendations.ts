import { Router } from "express";
import { optionalAuth, type AuthedRequest } from "../middleware/auth.js";
import { recommendationsQuerySchema } from "../schemas/recommendations.js";
import { getPopular, getRelated } from "../services/recommendations.js";

/**
 * Рекомендации витрины (ТЗ §6 FR-B12): /api/stores/:storeId/recommendations/*.
 * Публичное чтение (optionalAuth) — только активные товары. mergeParams — :storeId.
 */
export const recommendationsRouter: Router = Router({ mergeParams: true });

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

// Популярное в магазине (опц. по категории)
recommendationsRouter.get("/popular", optionalAuth, async (req: AuthedRequest, res, next) => {
  try {
    const { limit, category } = recommendationsQuerySchema.parse(req.query);
    const items = await getPopular(param(req, "storeId"), limit, category);
    res.json({ items });
  } catch (err) {
    next(err);
  }
});

// Похожие на товар
recommendationsRouter.get(
  "/related/:productId",
  optionalAuth,
  async (req: AuthedRequest, res, next) => {
    try {
      const { limit } = recommendationsQuerySchema.parse(req.query);
      const items = await getRelated(param(req, "storeId"), param(req, "productId"), limit);
      res.json({ items });
    } catch (err) {
      next(err);
    }
  },
);
