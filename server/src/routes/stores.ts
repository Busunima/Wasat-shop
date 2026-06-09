import { Router } from "express";
import { requireAuth, type AuthedRequest } from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { ApiError } from "../middleware/errorHandler.js";
import { storeInitSchema } from "../schemas/store.js";
import { createStore } from "../services/stores.js";

export const storesRouter: Router = Router();

/**
 * POST /api/stores/init — создание магазина (ТЗ §4.1 шаг 4, §9).
 * Auth: Firebase ID Token + App Check. Транзакционное создание + Custom Claim +
 * старт онбординга Stripe Connect — в services/stores.createStore.
 */
storesRouter.post("/init", verifyAppCheck, requireAuth, async (req: AuthedRequest, res, next) => {
  try {
    const body = storeInitSchema.parse(req.body);
    const uid = req.uid;
    const email = (req.claims?.["email"] as string | undefined) ?? "";
    if (!uid) throw new ApiError("UNAUTHENTICATED", "Нет uid после аутентификации");

    const result = await createStore(uid, email, body);
    res.status(201).json(result);
  } catch (err) {
    next(err);
  }
});
