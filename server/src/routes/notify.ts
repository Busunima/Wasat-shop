import { Router } from "express";
import { requireAuth, requireStoreRole, type AuthedRequest } from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { broadcastSchema } from "../schemas/notify.js";
import { broadcastToStore } from "../services/notifications.js";

/**
 * Рассылки магазина (ТЗ §6 FR-A07): POST /api/stores/:storeId/notify.
 * Только владелец. Возвращает статистику доставки. mergeParams — :storeId.
 */
export const notifyRouter: Router = Router({ mergeParams: true });

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

notifyRouter.post(
  "/",
  verifyAppCheck,
  requireAuth,
  requireStoreRole,
  async (req: AuthedRequest, res, next) => {
    try {
      const { title, body, segment } = broadcastSchema.parse(req.body);
      res.json(await broadcastToStore(param(req, "storeId"), title, body, segment));
    } catch (err) {
      next(err);
    }
  },
);
