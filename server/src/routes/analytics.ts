import { Router } from "express";
import {
  optionalAuth,
  requireAuth,
  requireStoreRole,
  type AuthedRequest,
} from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { analyticsEventSchema, analyticsQuerySchema } from "../schemas/analytics.js";
import { getAnalytics, recordEvent } from "../services/analytics.js";

/**
 * Продуктовые события и аналитика магазина (ТЗ §16, FR-A05):
 * /api/stores/:storeId/events (фиксация) и /analytics (дашборд владельца).
 * mergeParams — доступ к :storeId родительского роутера.
 */
export const analyticsRouter: Router = Router({ mergeParams: true });

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

// Фиксация события витрины (доступно гостю; App Check защищает от спама)
analyticsRouter.post(
  "/events",
  verifyAppCheck,
  optionalAuth,
  async (req: AuthedRequest, res, next) => {
    try {
      const event = analyticsEventSchema.parse(req.body);
      await recordEvent(param(req, "storeId"), event);
      res.status(202).json({ accepted: true });
    } catch (err) {
      next(err);
    }
  },
);

// Дашборд аналитики — только владелец
analyticsRouter.get(
  "/analytics",
  verifyAppCheck,
  requireAuth,
  requireStoreRole,
  async (req: AuthedRequest, res, next) => {
    try {
      const query = analyticsQuerySchema.parse(req.query);
      res.json(await getAnalytics(param(req, "storeId"), query));
    } catch (err) {
      next(err);
    }
  },
);
