import assert from "node:assert/strict";
import { test } from "node:test";
import { generateDescription } from "../../src/services/ai.ts";
import { ApiError } from "../../src/middleware/errorHandler.ts";

/**
 * Интеграционный тест FR-A12: без ANTHROPIC_API_KEY сервис отвечает
 * NOT_IMPLEMENTED (501) — env-gated паттерн, как Stripe-онбординг.
 */

test("generateDescription без ключа → NOT_IMPLEMENTED", async () => {
  await assert.rejects(
    () => generateDescription({ name: "Кеды", tags: [], language: "ru" }, "Магазин"),
    (err: unknown) => err instanceof ApiError && err.code === "NOT_IMPLEMENTED",
  );
});
