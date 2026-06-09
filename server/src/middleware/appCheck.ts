import type { NextFunction, Response } from "express";
import { getAppCheck } from "firebase-admin/app-check";
import { firebaseApp } from "../lib/firebase.js";
import { env } from "../config/env.js";
import { ApiError } from "./errorHandler.js";
import type { AuthedRequest } from "./auth.js";

/**
 * Проверка Firebase App Check токена (ТЗ §13: App Check на каждом запросе).
 * Токен передаётся в заголовке X-Firebase-AppCheck.
 *
 * Энфорсмент env-gated: в staging/production обязателен; в development/test
 * пропускается, чтобы интеграционные тесты против эмулятора не требовали реальный
 * App Check токен (эмулятор его не выдаёт).
 */
const ENFORCED = env.NODE_ENV === "staging" || env.NODE_ENV === "production";

export async function verifyAppCheck(
  req: AuthedRequest,
  _res: Response,
  next: NextFunction,
): Promise<void> {
  if (!ENFORCED) {
    next();
    return;
  }

  try {
    const token = req.header("x-firebase-appcheck");
    if (!token) {
      throw new ApiError("APP_CHECK_FAILED", "Отсутствует App Check токен");
    }
    await getAppCheck(firebaseApp()).verifyToken(token);
    next();
  } catch (err) {
    if (err instanceof ApiError) next(err);
    else next(new ApiError("APP_CHECK_FAILED", "Невалидный App Check токен"));
  }
}
