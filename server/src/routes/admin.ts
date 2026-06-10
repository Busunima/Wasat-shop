import { Router } from "express";
import { requireAuth, requireSuperadmin, type AuthedRequest } from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { ApiError } from "../middleware/errorHandler.js";
import {
  adminBlockSchema,
  adminPlanSchema,
  adminStoreListQuerySchema,
} from "../schemas/admin.js";
import { listStores, setStoreBlocked, setStorePlan } from "../services/admin.js";

/**
 * Суперадмин-эндпоинты (ТЗ §7, §9). Цепочка: App Check → ID Token → claim superadmin.
 * Используется веб-панелью admin-web (React/TS SPA).
 */
export const adminRouter: Router = Router();

adminRouter.use(verifyAppCheck, requireAuth, requireSuperadmin);

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

function actorUid(req: AuthedRequest): string {
  const uid = req.uid;
  if (!uid) throw new ApiError("UNAUTHENTICATED", "Нет uid после аутентификации");
  return uid;
}

// FR-S01: список всех магазинов с поиском/фильтрами
adminRouter.get("/stores", async (req: AuthedRequest, res, next) => {
  try {
    const query = adminStoreListQuerySchema.parse(req.query);
    res.json(await listStores(query));
  } catch (err) {
    next(err);
  }
});

// FR-S02: блокировка/разблокировка магазина
adminRouter.patch("/stores/:storeId/block", async (req: AuthedRequest, res, next) => {
  try {
    const { blocked, reason } = adminBlockSchema.parse(req.body);
    res.json(await setStoreBlocked(actorUid(req), param(req, "storeId"), blocked, reason));
  } catch (err) {
    next(err);
  }
});

// FR-S02/S03: смена тарифа магазина
adminRouter.patch("/stores/:storeId/plan", async (req: AuthedRequest, res, next) => {
  try {
    const { plan } = adminPlanSchema.parse(req.body);
    res.json(await setStorePlan(actorUid(req), param(req, "storeId"), plan));
  } catch (err) {
    next(err);
  }
});
