import { Router } from "express";
import { requireAuth, type AuthedRequest } from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { ApiError } from "../middleware/errorHandler.js";
import { pushTokenSchema } from "../schemas/push.js";
import { registerPushToken } from "../services/push.js";

/**
 * FCM-токены покупателя (ТЗ §6 FR-B10): POST /api/stores/:storeId/push-tokens.
 * Любой авторизованный пользователь регистрирует токен своего устройства.
 */
export const pushRouter: Router = Router({ mergeParams: true });

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

pushRouter.post("/", verifyAppCheck, requireAuth, async (req: AuthedRequest, res, next) => {
  try {
    const uid = req.uid;
    if (!uid) throw new ApiError("UNAUTHENTICATED", "Нет uid после аутентификации");
    const { token, platform } = pushTokenSchema.parse(req.body);
    await registerPushToken(param(req, "storeId"), uid, token, platform);
    res.status(204).end();
  } catch (err) {
    next(err);
  }
});
