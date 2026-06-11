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

/**
 * Опциональная аутентификация для публичных эндпоинтов с расширенным режимом для
 * владельца (например, листинг товаров: посетителю — active, владельцу — все).
 * Невалидный/отсутствующий токен НЕ является ошибкой — запрос идёт как анонимный.
 */
export async function optionalAuth(
  req: AuthedRequest,
  _res: Response,
  next: NextFunction,
): Promise<void> {
  const header = req.header("authorization") ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : null;
  if (token) {
    try {
      const decoded = await auth().verifyIdToken(token);
      req.uid = decoded.uid;
      req.claims = decoded as unknown as Record<string, unknown>;
    } catch {
      // анонимный доступ
    }
  }
  next();
}

/**
 * Фабрика проверки членства в магазине (ТЗ §4.2, §13, FR-A09): Custom Claims
 * должны давать claims.storeId === :storeId и роль из набора allowedRoles.
 * Ставится ПОСЛЕ requireAuth.
 */
export function requireStoreMember(...allowedRoles: string[]) {
  return (req: AuthedRequest, _res: Response, next: NextFunction): void => {
    const storeId = req.params["storeId"];
    const role = req.claims?.["role"];
    const ok =
      typeof storeId === "string" &&
      req.claims?.["storeId"] === storeId &&
      typeof role === "string" &&
      allowedRoles.includes(role);
    if (ok) next();
    else next(new ApiError("FORBIDDEN", "Нет прав на управление этим магазином"));
  };
}

/** Только владелец магазина (настройки, биллинг, промокоды, управление сотрудниками). */
export const requireStoreRole = requireStoreMember("owner");

/** Владелец или сотрудник (каталог, остатки, заказы) — FR-A09. */
export const requireStoreStaff = requireStoreMember("owner", "manager", "staff");

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
