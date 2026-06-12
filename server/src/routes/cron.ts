import { Router } from "express";
import { requireCronSecret } from "../middleware/auth.js";
import { env } from "../config/env.js";
import { runAbandonedCartReminders } from "../services/notifications.js";
import { runCleanup } from "../services/cleanup.js";

/**
 * Системные cron-задачи (ТЗ §9, §13): /api/cron/*. Аутентификация — заголовок
 * x-cron-secret (CRON_SECRET, без fallback в production). Запускается Cloud Scheduler.
 */
export const cronRouter: Router = Router();

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

// FR-A07: напоминание о брошенной корзине (старше 24ч)
cronRouter.post("/abandoned-carts", requireCronSecret(env.CRON_SECRET), async (_req, res, next) => {
  try {
    res.json(await runAbandonedCartReminders(ONE_DAY_MS));
  } catch (err) {
    next(err);
  }
});

// §9: очистка устаревших журналов (auditLog/inventoryLog) старше окна хранения
cronRouter.post("/cleanup", requireCronSecret(env.CRON_SECRET), async (_req, res, next) => {
  try {
    res.json(await runCleanup());
  } catch (err) {
    next(err);
  }
});
