import express from "express";
import { env } from "./config/env.js";
import { apiRouter } from "./routes/index.js";
import { errorHandler } from "./middleware/errorHandler.js";
import { logger } from "./lib/logger.js";

/**
 * Bootstrap Express-сервера (ТЗ §2, слой 2). Деплой — Cloud Run.
 * Вебхуки используют raw-парсер внутри своего роутера, поэтому JSON-парсер вешаем
 * на /api, исключая вебхуки (они подключены до общего json через собственный raw).
 */
export function createApp(): express.Express {
  const app = express();

  // Health-check для Cloud Run / uptime checks (ТЗ §15).
  app.get("/healthz", (_req, res) => {
    res.json({ status: "ok", env: env.NODE_ENV });
  });

  // JSON-парсер для всех маршрутов, КРОМЕ вебхуков Stripe: им нужно raw-тело для
  // верификации подписи (их роутер ставит express.raw() на уровне маршрута).
  app.use((req, res, next) => {
    if (req.path.startsWith("/api/webhooks/")) return next();
    return express.json({ limit: "1mb" })(req, res, next);
  });

  app.use("/api", apiRouter);

  app.use(errorHandler);
  return app;
}

// Запуск только при прямом вызове (не при импорте в тестах).
const isMain = import.meta.url === `file://${process.argv[1]}`;
if (isMain) {
  const app = createApp();
  app.listen(env.PORT, () => {
    logger.info(`Server слушает порт ${env.PORT} (${env.NODE_ENV})`);
  });
}
