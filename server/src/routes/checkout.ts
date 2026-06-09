import { Router } from "express";
import { requireAuth } from "../middleware/auth.js";
import { rateLimit } from "../middleware/rateLimit.js";
import { ApiError } from "../middleware/errorHandler.js";

export const checkoutRouter: Router = Router();

/**
 * POST /api/checkout — единственный путь создания заказа (ТЗ §FR-B05, §10.1).
 * Серверная транзакция: атомарное списание стока + расчёт налога (Stripe Tax) +
 * валидация промокода + создание Stripe PaymentIntent. Идемпотентность по idempotencyKey.
 *
 * Заглушка: контракт и middleware готовы, бизнес-логика — Фаза 4.
 */
checkoutRouter.post("/", rateLimit({ max: 30 }), requireAuth, (_req, _res, next) => {
  next(
    new ApiError("NOT_IMPLEMENTED", "checkout: транзакционный чекаут реализуется в Фазе 4"),
  );
});
