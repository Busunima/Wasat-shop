import type { NextFunction, Request, Response } from "express";

/**
 * Заглушка rate-limiting (ТЗ §13: общий счётчик в Memorystore).
 * В Шаге/Фазе реализации заменяется на Redis-инкремент с окном и TTL.
 * Сейчас — no-op, чтобы цепочка middleware была собрана.
 */
export function rateLimit(_opts?: { windowMs?: number; max?: number }) {
  return (_req: Request, _res: Response, next: NextFunction): void => {
    // TODO(Фаза 1): INCR ключа в Memorystore, при превышении — ApiError("RATE_LIMITED").
    next();
  };
}
