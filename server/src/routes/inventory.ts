import express, { Router } from "express";
import { requireAuth, requireStoreStaff, type AuthedRequest } from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { ApiError } from "../middleware/errorHandler.js";
import { stockAdjustSchema } from "../schemas/inventory.js";
import { adjustStock, importStockCsv, listInventoryLog } from "../services/inventory.js";
import { z } from "zod";

/**
 * Инвентарь магазина (ТЗ §6 FR-A03): /api/stores/:storeId/inventory/*.
 * Только владелец (staff — Фаза 4 вместе с ролями).
 */
export const inventoryRouter: Router = Router({ mergeParams: true });

inventoryRouter.use(verifyAppCheck, requireAuth, requireStoreStaff);

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

function actorUid(req: AuthedRequest): string {
  const uid = req.uid;
  if (!uid) throw new ApiError("UNAUTHENTICATED", "Нет uid после аутентификации");
  return uid;
}

// Корректировка остатка товара/варианта (дельта)
inventoryRouter.post("/products/:productId/stock", async (req: AuthedRequest, res, next) => {
  try {
    const body = stockAdjustSchema.parse(req.body);
    res.json(await adjustStock(param(req, "storeId"), param(req, "productId"), actorUid(req), body));
  } catch (err) {
    next(err);
  }
});

// Bulk CSV-импорт остатков: body = text/csv (sku,stock)
inventoryRouter.post(
  "/import",
  express.text({ type: ["text/csv", "text/plain"], limit: "1mb" }),
  async (req: AuthedRequest, res, next) => {
    try {
      const csv = typeof req.body === "string" ? req.body : String(req.body ?? "");
      if (!csv.trim()) throw new ApiError("VALIDATION_ERROR", "Пустой CSV");
    res.json(await importStockCsv(param(req, "storeId"), actorUid(req), csv));
    } catch (err) {
      next(err);
    }
  },
);

const logQuerySchema = z.object({
  productId: z.string().optional(),
  limit: z.coerce.number().int().min(1).max(200).default(50),
});

// История изменений остатков
inventoryRouter.get("/log", async (req: AuthedRequest, res, next) => {
  try {
    const { productId, limit } = logQuerySchema.parse(req.query);
    res.json({ items: await listInventoryLog(param(req, "storeId"), productId, limit) });
  } catch (err) {
    next(err);
  }
});
