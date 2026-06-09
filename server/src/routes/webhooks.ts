import { Router, raw } from "express";
import { ApiError } from "../middleware/errorHandler.js";

export const webhooksRouter: Router = Router();

/**
 * Вебхуки Stripe (ТЗ §9, §10). Аутентификация — проверкой подписи Stripe, БЕЗ Firebase-токена.
 * Тело должно читаться как raw buffer для верификации подписи (constructEvent).
 * Секреты — без fallback в production (ТЗ §13).
 *
 * Заглушки: каркас роутера и raw-парсер готовы; обработка событий — Фаза 4.
 */

// Платежи и возвраты.
webhooksRouter.post("/stripe", raw({ type: "application/json" }), (_req, _res, next) => {
  // TODO(Фаза 4): stripe.webhooks.constructEvent(body, sig, STRIPE_WEBHOOK_SECRET) → switch по типу.
  next(new ApiError("NOT_IMPLEMENTED", "webhooks/stripe: обработка событий — Фаза 4"));
});

// Подписки SaaS (Stripe Billing).
webhooksRouter.post("/stripe-billing", raw({ type: "application/json" }), (_req, _res, next) => {
  next(new ApiError("NOT_IMPLEMENTED", "webhooks/stripe-billing: биллинг подписок — Фаза 4"));
});
