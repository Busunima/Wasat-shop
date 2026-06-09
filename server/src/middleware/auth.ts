import type { NextFunction, Request, Response } from "express";
import { auth } from "../lib/firebase.js";
import { ApiError } from "./errorHandler.js";

/**
 * Аутентификация запроса (ТЗ §4, §13):
 *  - Authorization: Bearer {Firebase ID Token} → verifyIdToken
 *  - App Check token (X-Firebase-AppCheck) — проверяется отдельно
 *  - роль читается из Custom Claims (владелец storeId / superadmin) — выставляет ТОЛЬКО сервер
 *
 * Реальная проверка App Check (appCheck().verifyToken) и членства staff подключается в Шаге 2.
 */
export interface AuthedRequest extends Request {
  uid?: string;
  claims?: Record<string, unknown>;
}

export async function requireAuth(
  req: AuthedRequest,
  _res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const header = req.header("authorization") ?? "";
    const token = header.startsWith("Bearer ") ? header.slice(7) : null;
    if (!token) {
      throw new ApiError("UNAUTHENTICATED", "Отсутствует Firebase ID Token");
    }

    const decoded = await auth().verifyIdToken(token);
    req.uid = decoded.uid;
    req.claims = decoded as unknown as Record<string, unknown>;
    next();
  } catch (err) {
    if (err instanceof ApiError) next(err);
    else next(new ApiError("UNAUTHENTICATED", "Невалидный ID Token"));
  }
}

/** Требует роль суперадмина (Custom Claim superadmin: true) — ТЗ §7. */
export function requireSuperadmin(req: AuthedRequest, _res: Response, next: NextFunction): void {
  if (req.claims?.["superadmin"] === true) next();
  else next(new ApiError("FORBIDDEN", "Требуется роль суперадмина"));
}

/** Проверка cron-секрета для /api/cron/* (ТЗ §9, §13: без fallback в prod). */
export function requireCronSecret(expected: string | undefined) {
  return (req: Request, _res: Response, next: NextFunction): void => {
    if (expected && req.header("x-cron-secret") === expected) next();
    else next(new ApiError("FORBIDDEN", "Невалидный CRON_SECRET"));
  };
}
