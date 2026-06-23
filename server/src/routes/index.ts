import { Router } from "express";
import { storesRouter } from "./stores.js";
import { checkoutRouter } from "./checkout.js";
import { webhooksRouter } from "./webhooks.js";
import { adminRouter } from "./admin.js";
import { accountRouter } from "./account.js";
import { cronRouter } from "./cron.js";

/**
 * Сборка всех маршрутов под /api (ТЗ §9).
 * По мере Фаз добавляются: orders, returns, notify, staff, stripe/onboard-link,
 * search/reindex, cron/*.
 */
export const apiRouter: Router = Router();

apiRouter.use("/stores", storesRouter);
apiRouter.use("/checkout", checkoutRouter);
apiRouter.use("/webhooks", webhooksRouter);
apiRouter.use("/admin", adminRouter);
apiRouter.use("/account", accountRouter);
apiRouter.use("/cron", cronRouter);
