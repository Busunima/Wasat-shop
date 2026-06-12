import type { NextFunction, Response } from "express";
import { ApiError } from "./errorHandler.js";
import type { AuthedRequest } from "./auth.js";

/**
 * Rate-limiting (ТЗ §13). Реализация — фиксированное окно in-memory: достаточно
 * для одного инстанса Cloud Run (MVP). На мульти-инстанс выносится в Memorystore
 * (INCR+TTL) тем же контрактом hit(); см. docs/decisions.md. Ядро (fixedWindowHit)
 * чистое и покрыто unit-тестами; ключ — uid (если авторизован) либо IP.
 */

export interface RateLimitState {
  count: number;
  resetAt: number;
}

export interface RateLimitResult {
  allowed: boolean;
  remaining: number;
  resetAt: number;
}

/**
 * Чистый шаг фиксированного окна. Мутирует переданный store (Map). Возвращает
 * решение и метаданные для заголовков. now/windowMs/max инъецируются — тестируемо.
 */
export function fixedWindowHit(
  store: Map<string, RateLimitState>,
  key: string,
  now: number,
  windowMs: number,
  max: number,
): RateLimitResult {
  const existing = store.get(key);
  if (!existing || now >= existing.resetAt) {
    const resetAt = now + windowMs;
    store.set(key, { count: 1, resetAt });
    return { allowed: true, remaining: max - 1, resetAt };
  }
  existing.count += 1;
  const allowed = existing.count <= max;
  return {
    allowed,
    remaining: Math.max(0, max - existing.count),
    resetAt: existing.resetAt,
  };
}

/** Удаление протухших окон (вызывается лениво по таймеру), чтобы Map не рос. */
export function sweepExpired(store: Map<string, RateLimitState>, now: number): void {
  for (const [key, state] of store) {
    if (now >= state.resetAt) store.delete(key);
  }
}

function clientKey(req: AuthedRequest): string {
  if (req.uid) return `uid:${req.uid}`;
  // Express req.ip учитывает trust proxy; запасной вариант — socket-адрес.
  return `ip:${req.ip ?? req.socket?.remoteAddress ?? "unknown"}`;
}

/**
 * Middleware фиксированного окна. По умолчанию 60 запросов/мин на ключ. Если стоит
 * ДО requireAuth, ключ для анонимов — IP; на авторизованных эндпоинтах лимит
 * действует по IP (uid появляется в цепочке позже).
 */
export function rateLimit(opts?: { windowMs?: number; max?: number }) {
  const windowMs = opts?.windowMs ?? 60_000;
  const max = opts?.max ?? 60;
  const store = new Map<string, RateLimitState>();
  let lastSweep = 0;

  return (req: AuthedRequest, res: Response, next: NextFunction): void => {
    const now = Date.now();
    if (now - lastSweep > windowMs) {
      sweepExpired(store, now);
      lastSweep = now;
    }

    const result = fixedWindowHit(store, clientKey(req), now, windowMs, max);
    res.setHeader("RateLimit-Limit", String(max));
    res.setHeader("RateLimit-Remaining", String(result.remaining));
    res.setHeader("RateLimit-Reset", String(Math.ceil((result.resetAt - now) / 1000)));

    if (!result.allowed) {
      res.setHeader("Retry-After", String(Math.ceil((result.resetAt - now) / 1000)));
      next(new ApiError("RATE_LIMITED", "Слишком много запросов, попробуйте позже"));
      return;
    }
    next();
  };
}
