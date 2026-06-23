import { Router } from "express";
import { requireAuth, type AuthedRequest } from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { ApiError } from "../middleware/errorHandler.js";
import { deleteUserData, exportUserData } from "../services/account.js";

/**
 * Аккаунт покупателя (GDPR, ТЗ §13): экспорт своих данных и удаление аккаунта.
 * Действия — только над собственным uid (requireAuth).
 */
export const accountRouter: Router = Router();

// GET /api/account/export — выгрузка персональных данных (право на доступ).
accountRouter.get("/export", verifyAppCheck, requireAuth, async (req: AuthedRequest, res, next) => {
  try {
    const uid = req.uid;
    if (!uid) throw new ApiError("UNAUTHENTICATED", "Нужна аутентификация");
    res.json(await exportUserData(uid));
  } catch (err) {
    next(err);
  }
});

// DELETE /api/account — удаление аккаунта + анонимизация заказов (право на забвение).
accountRouter.delete("/", verifyAppCheck, requireAuth, async (req: AuthedRequest, res, next) => {
  try {
    const uid = req.uid;
    if (!uid) throw new ApiError("UNAUTHENTICATED", "Нужна аутентификация");
    res.json(await deleteUserData(uid));
  } catch (err) {
    next(err);
  }
});
