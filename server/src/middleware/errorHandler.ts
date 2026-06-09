import type { NextFunction, Request, Response } from "express";
import { ZodError } from "zod";
import { logger } from "../lib/logger.js";

/**
 * Единый контракт ошибок (docs/api-contract.md):
 * { "error": { "code", "message", "details" } }
 */
export type ApiErrorCode =
  | "VALIDATION_ERROR"
  | "UNAUTHENTICATED"
  | "APP_CHECK_FAILED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "CONFLICT"
  | "OUT_OF_STOCK"
  | "PROMO_INVALID"
  | "RATE_LIMITED"
  | "NOT_IMPLEMENTED"
  | "INTERNAL";

const STATUS: Record<ApiErrorCode, number> = {
  VALIDATION_ERROR: 400,
  UNAUTHENTICATED: 401,
  APP_CHECK_FAILED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  OUT_OF_STOCK: 422,
  PROMO_INVALID: 422,
  RATE_LIMITED: 429,
  NOT_IMPLEMENTED: 501,
  INTERNAL: 500,
};

export class ApiError extends Error {
  constructor(
    readonly code: ApiErrorCode,
    message: string,
    readonly details?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }

  get status(): number {
    return STATUS[this.code];
  }
}

// Express определяет error-handler по сигнатуре из 4 аргументов — _next обязателен.
export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction): void {
  if (err instanceof ZodError) {
    res.status(400).json({
      error: { code: "VALIDATION_ERROR", message: "Невалидный запрос", details: err.issues },
    });
    return;
  }

  if (err instanceof ApiError) {
    res.status(err.status).json({
      error: { code: err.code, message: err.message, details: err.details ?? null },
    });
    return;
  }

  logger.error("Необработанная ошибка", { err: String(err) });
  res.status(500).json({
    error: { code: "INTERNAL", message: "Внутренняя ошибка сервера", details: null },
  });
}
