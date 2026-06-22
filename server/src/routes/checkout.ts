import { Router } from "express";
import { requireAuth, type AuthedRequest } from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { rateLimit } from "../middleware/rateLimit.js";
import { ApiError } from "../middleware/errorHandler.js";
import { checkoutSchema } from "../schemas/order.js";
import { createOrder } from "../services/orders.js";

export const checkoutRouter: Router = Router();

/**
 * POST /api/checkout — единственный путь создания заказа (ТЗ §FR-B05, §10.1).
 * Серверная транзакция: пересчёт цен → атомарное списание стока → промокод →
 * создание заказа. Идемпотентность по idempotencyKey (повтор → 200 + тот же заказ).
 * Оплата deferred; Stripe PaymentIntent/Tax встраиваются при появлении ключей.
 */
checkoutRouter.post(
  "/",
  rateLimit({ max: 30 }),
  verifyAppCheck,
  requireAuth,
  async (req: AuthedRequest, res, next) => {
    try {
      const input = checkoutSchema.parse(req.body);
      const uid = req.uid;
      if (!uid) throw new ApiError("UNAUTHENTICATED", "Нет uid после аутентификации");
      const email = (req.claims?.["email"] as string | undefined) ?? "";

      const { order, replay, clientSecret } = await createOrder(uid, email, input);
      // clientSecret — для Stripe PaymentSheet (FR-B05); null без ключей (deferred).
      // Клиент игнорирует неизвестные поля, поэтому ответ обратно совместим.
      res.status(replay ? 200 : 201).json({ ...order, clientSecret });
    } catch (err) {
      next(err);
    }
  },
);
