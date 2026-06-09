import { Router } from "express";
import { storesRouter } from "./stores.js";
import { checkoutRouter } from "./checkout.js";
import { webhooksRouter } from "./webhooks.js";

/**
 * Сборка всех маршрутов под /api (ТЗ §9).
 * По мере Фаз добавляются: orders, returns, notify, staff, stripe/onboard-link,
 * search/reindex, admin/*, cron/*.
 */
export const apiRouter: Router = Router();

apiRouter.use("/stores", storesRouter);
apiRouter.use("/checkout", checkoutRouter);
apiRouter.use("/webhooks", webhooksRouter);
