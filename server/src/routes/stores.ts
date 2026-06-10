import { Router } from "express";
import {
  optionalAuth,
  requireAuth,
  requireStoreRole,
  type AuthedRequest,
} from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { ApiError } from "../middleware/errorHandler.js";
import { storeInitSchema, storeUpdateSchema } from "../schemas/store.js";
import { createStore, getStoreInfo, resolveSlug, updateStore } from "../services/stores.js";
import { productsRouter } from "./products.js";
import { inventoryRouter } from "./inventory.js";

export const storesRouter: Router = Router();

// Товары магазина: /api/stores/:storeId/products (FR-A02 + витрина)
storesRouter.use("/:storeId/products", productsRouter);

// Инвентарь: /api/stores/:storeId/inventory (FR-A03)
storesRouter.use("/:storeId/inventory", inventoryRouter);

/**
 * GET /api/stores/by-slug/:slug — резолв витрины по slug (FR-B01: deep link / QR).
 * Public-правило видимости. Объявлен ДО GET /:storeId (иначе перехват параметром).
 */
storesRouter.get("/by-slug/:slug", async (req, res, next) => {
  try {
    res.json(await resolveSlug(String(req.params["slug"] ?? "")));
  } catch (err) {
    next(err);
  }
});

/**
 * PATCH /api/stores/:storeId — настройки магазина (FR-A01), только владелец.
 * slug/currency/plan не изменяются (см. storeUpdateSchema).
 */
storesRouter.patch(
  "/:storeId",
  verifyAppCheck,
  requireAuth,
  requireStoreRole,
  async (req: AuthedRequest, res, next) => {
    try {
      const body = storeUpdateSchema.parse(req.body);
      res.json(await updateStore(String(req.params["storeId"] ?? ""), body));
    } catch (err) {
      next(err);
    }
  },
);

/**
 * GET /api/stores/:storeId — публичная карточка магазина (витрина).
 * Посетителю — только isPublic && !isBlocked; владельцу — всегда (optionalAuth).
 */
storesRouter.get("/:storeId", optionalAuth, async (req: AuthedRequest, res, next) => {
  try {
    const storeId = String(req.params["storeId"] ?? "");
    const isOwner = req.claims?.["storeId"] === storeId && req.claims?.["role"] === "owner";
    res.json(await getStoreInfo(storeId, isOwner));
  } catch (err) {
    next(err);
  }
});

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
