import assert from "node:assert/strict";
import { test } from "node:test";
import {
  fixedWindowHit,
  sweepExpired,
  type RateLimitState,
} from "../../src/middleware/rateLimit.ts";

const WINDOW = 60_000;

test("fixedWindowHit: пропускает до лимита, затем блокирует", () => {
  const store = new Map<string, RateLimitState>();
  const now = 1_000_000;
  // max=3: три запроса проходят
  assert.equal(fixedWindowHit(store, "k", now, WINDOW, 3).allowed, true);
  assert.equal(fixedWindowHit(store, "k", now, WINDOW, 3).allowed, true);
  const third = fixedWindowHit(store, "k", now, WINDOW, 3);
  assert.equal(third.allowed, true);
  assert.equal(third.remaining, 0);
  // четвёртый — за лимитом
  const fourth = fixedWindowHit(store, "k", now, WINDOW, 3);
  assert.equal(fourth.allowed, false);
  assert.equal(fourth.remaining, 0);
});

test("fixedWindowHit: окно сбрасывается по истечении resetAt", () => {
  const store = new Map<string, RateLimitState>();
  const start = 1_000_000;
  fixedWindowHit(store, "k", start, WINDOW, 1);
  // в том же окне — блок
  assert.equal(fixedWindowHit(store, "k", start + 100, WINDOW, 1).allowed, false);
  // после окна — снова разрешено, счётчик с нуля
  const after = fixedWindowHit(store, "k", start + WINDOW, WINDOW, 1);
  assert.equal(after.allowed, true);
  assert.equal(after.remaining, 0);
});

test("fixedWindowHit: ключи изолированы друг от друга", () => {
  const store = new Map<string, RateLimitState>();
  const now = 1_000_000;
  assert.equal(fixedWindowHit(store, "a", now, WINDOW, 1).allowed, true);
  assert.equal(fixedWindowHit(store, "a", now, WINDOW, 1).allowed, false);
  // другой ключ — собственный лимит
  assert.equal(fixedWindowHit(store, "b", now, WINDOW, 1).allowed, true);
});

test("fixedWindowHit: remaining и resetAt в метаданных", () => {
  const store = new Map<string, RateLimitState>();
  const now = 5_000;
  const r = fixedWindowHit(store, "k", now, WINDOW, 10);
  assert.equal(r.remaining, 9);
  assert.equal(r.resetAt, now + WINDOW);
});

test("sweepExpired: удаляет только протухшие окна", () => {
  const store = new Map<string, RateLimitState>();
  store.set("live", { count: 1, resetAt: 2_000 });
  store.set("dead", { count: 5, resetAt: 1_000 });
  sweepExpired(store, 1_500);
  assert.equal(store.has("live"), true);
  assert.equal(store.has("dead"), false);
});
