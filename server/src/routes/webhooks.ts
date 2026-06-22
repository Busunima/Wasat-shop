import { Router, raw, type Request, type Response, type NextFunction } from "express";
import { ApiError } from "../middleware/errorHandler.js";
import { env } from "../config/env.js";
import { getStripe } from "../services/stripe.js";
import { markOrderPaid } from "../services/orders.js";
import { logger } from "../lib/logger.js";

export const webhooksRouter: Router = Router();

/**
 * Вебхуки Stripe (ТЗ §9, §10). Аутентификация — проверкой подписи Stripe, БЕЗ Firebase-токена.
 * Тело должно читаться как raw buffer для верификации подписи (constructEvent).
 * Секреты — без fallback в production (ТЗ §13).
 */

// Платежи (FR-B05): payment_intent.succeeded → отметка заказа оплаченным.
webhooksRouter.post(
  "/stripe",
  raw({ type: "application/json" }),
  async (req: Request, res: Response, next: NextFunction) => {
    const stripe = getStripe();
    if (!stripe || !env.STRIPE_WEBHOOK_SECRET) {
      next(new ApiError("NOT_IMPLEMENTED", "webhooks/stripe: Stripe не сконфигурирован"));
      return;
    }
    const signature = req.headers["stripe-signature"];
    if (typeof signature !== "string") {
      next(new ApiError("VALIDATION_ERROR", "Нет заголовка stripe-signature"));
      return;
    }
    try {
      const event = stripe.webhooks.constructEvent(
        req.body as Buffer,
        signature,
        env.STRIPE_WEBHOOK_SECRET,
      );
      if (event.type === "payment_intent.succeeded") {
        const intent = event.data.object;
        const storeId = intent.metadata?.["storeId"];
        const orderId = intent.metadata?.["orderId"];
        if (storeId && orderId) {
          await markOrderPaid(storeId, orderId, intent.id);
          logger.info("Оплата подтверждена вебхуком", { storeId, orderId });
        }
      }
      res.json({ received: true });
    } catch {
      // Неверная подпись или ошибка обработки — отдаём 400, Stripe повторит.
      next(new ApiError("VALIDATION_ERROR", "Stripe webhook: проверка не пройдена"));
    }
  },
);

// Подписки SaaS (Stripe Billing).
webhooksRouter.post("/stripe-billing", raw({ type: "application/json" }), (_req, _res, next) => {
  next(new ApiError("NOT_IMPLEMENTED", "webhooks/stripe-billing: биллинг подписок — Фаза 4"));
});
