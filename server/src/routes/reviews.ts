import { Router } from "express";
import {
  optionalAuth,
  requireAuth,
  requireStoreRole,
  type AuthedRequest,
} from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { ApiError } from "../middleware/errorHandler.js";
import { reviewCreateSchema, reviewsListQuerySchema } from "../schemas/review.js";
import { createReview, deleteReview, listReviews } from "../services/reviews.js";

/**
 * Отзывы о товаре (ТЗ §6 FR-B08): /api/stores/:storeId/products/:productId/reviews.
 * Чтение публичное; создание — авторизованный покупатель (право проверяет сервис
 * по orderId); удаление — модерация владельца. mergeParams — :storeId/:productId.
 */
export const reviewsRouter: Router = Router({ mergeParams: true });

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

function actorUid(req: AuthedRequest): string {
  const uid = req.uid;
  if (!uid) throw new ApiError("UNAUTHENTICATED", "Нет uid после аутентификации");
  return uid;
}

// Список отзивов о товаре (публично)
reviewsRouter.get("/", optionalAuth, async (req: AuthedRequest, res, next) => {
  try {
    const { limit } = reviewsListQuerySchema.parse(req.query);
    const items = await listReviews(param(req, "storeId"), param(req, "productId"), limit);
    res.json({ items });
  } catch (err) {
    next(err);
  }
});

// Оставить/обновить отзыв (покупатель с доставленным заказом)
reviewsRouter.post("/", verifyAppCheck, requireAuth, async (req: AuthedRequest, res, next) => {
  try {
    const input = reviewCreateSchema.parse(req.body);
    res
      .status(201)
      .json(await createReview(param(req, "storeId"), param(req, "productId"), actorUid(req), input));
  } catch (err) {
    next(err);
  }
});

// Удаление отзыва (модерация — владелец/сотрудник)
reviewsRouter.delete(
  "/:reviewId",
  verifyAppCheck,
  requireAuth,
  requireStoreRole,
  async (req: AuthedRequest, res, next) => {
    try {
      await deleteReview(param(req, "storeId"), param(req, "reviewId"));
      res.status(204).end();
    } catch (err) {
      next(err);
    }
  },
);
