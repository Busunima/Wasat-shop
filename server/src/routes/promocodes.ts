import { Router } from "express";
import { z } from "zod";
import { requireAuth, requireStoreRole, type AuthedRequest } from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { promoCreateSchema, promoUpdateSchema } from "../schemas/promocode.js";
import {
  createPromocode,
  deletePromocode,
  listPromocodes,
  previewPromo,
  updatePromocode,
} from "../services/promocodes.js";

/**
 * Промокоды магазина (ТЗ §6 FR-A06): /api/stores/:storeId/promocodes/*.
 * CRUD — только владелец; предпросмотр скидки (/preview) — публичный (App Check),
 * чтобы покупатель видел результат до оформления (FR-B04). mergeParams — :storeId.
 */
export const promocodesRouter: Router = Router({ mergeParams: true });

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

/** Код в URL приходит в любом регистре; нормализуем к схеме (uppercase). */
function code(req: AuthedRequest): string {
  return param(req, "code").toUpperCase();
}

const previewSchema = z.object({
  code: z
    .string()
    .min(1)
    .max(32)
    .transform((c) => c.toUpperCase()),
  subtotal: z.number().int().min(0),
  itemProductIds: z.array(z.string().max(64)).max(500).default([]),
  itemCategories: z.array(z.string().max(80)).max(100).default([]),
});

// Предпросмотр скидки для корзины (публичный, App Check) — ДО owner-цепочки
promocodesRouter.post("/preview", verifyAppCheck, async (req: AuthedRequest, res, next) => {
  try {
    const { code: promoCode, ...input } = previewSchema.parse(req.body);
    res.json(await previewPromo(param(req, "storeId"), promoCode, input));
  } catch (err) {
    next(err);
  }
});

// Дальше — только владелец магазина
promocodesRouter.use(verifyAppCheck, requireAuth, requireStoreRole);

promocodesRouter.get("/", async (req: AuthedRequest, res, next) => {
  try {
    res.json({ items: await listPromocodes(param(req, "storeId")) });
  } catch (err) {
    next(err);
  }
});

promocodesRouter.post("/", async (req: AuthedRequest, res, next) => {
  try {
    const body = promoCreateSchema.parse(req.body);
    res.status(201).json(await createPromocode(param(req, "storeId"), body));
  } catch (err) {
    next(err);
  }
});

promocodesRouter.patch("/:code", async (req: AuthedRequest, res, next) => {
  try {
    const patch = promoUpdateSchema.parse(req.body);
    res.json(await updatePromocode(param(req, "storeId"), code(req), patch));
  } catch (err) {
    next(err);
  }
});

promocodesRouter.delete("/:code", async (req: AuthedRequest, res, next) => {
  try {
    await deletePromocode(param(req, "storeId"), code(req));
    res.status(204).end();
  } catch (err) {
    next(err);
  }
});
