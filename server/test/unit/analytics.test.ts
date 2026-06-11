import assert from "node:assert/strict";
import { test } from "node:test";
import {
  analyticsEventSchema,
  analyticsQuerySchema,
  dateRange,
  daysAgoUtc,
  todayUtc,
} from "../../src/schemas/analytics.ts";

test("analyticsEventSchema: типы и опциональные поля", () => {
  assert.equal(analyticsEventSchema.parse({ type: "product_view", productId: "p1" }).type, "product_view");
  assert.equal(analyticsEventSchema.parse({ type: "purchase", value: 9990 }).value, 9990);
  assert.throws(() => analyticsEventSchema.parse({ type: "unknown" }));
  assert.throws(() => analyticsEventSchema.parse({ type: "purchase", value: -1 }));
});

test("analyticsQuerySchema: формат дат YYYY-MM-DD", () => {
  assert.deepEqual(analyticsQuerySchema.parse({ from: "2026-06-01", to: "2026-06-10" }), {
    from: "2026-06-01",
    to: "2026-06-10",
  });
  assert.throws(() => analyticsQuerySchema.parse({ from: "01-06-2026" }));
});

test("dateRange: включительный диапазон, защита от инверсии и переполнения", () => {
  assert.deepEqual(dateRange("2026-06-08", "2026-06-10"), [
    "2026-06-08",
    "2026-06-09",
    "2026-06-10",
  ]);
  assert.deepEqual(dateRange("2026-06-10", "2026-06-08"), []); // from > to
  assert.equal(dateRange("2026-06-10", "2026-06-10").length, 1);
  assert.ok(dateRange("2020-01-01", "2030-01-01").length <= 366); // кап
});

test("todayUtc/daysAgoUtc: формат и сдвиг", () => {
  const now = new Date("2026-06-10T12:00:00Z");
  assert.equal(todayUtc(now), "2026-06-10");
  assert.equal(daysAgoUtc(29, now), "2026-05-12");
});
