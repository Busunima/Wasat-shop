import assert from "node:assert/strict";
import { test } from "node:test";
import {
  summarizeAnalyticsDocs,
  type AnalyticsRow,
  type StoreMeta,
} from "../../src/services/platformAnalytics.ts";

const stores = new Map<string, StoreMeta>([
  ["s1", { name: "Alpha", slug: "alpha" }],
  ["s2", { name: "Beta", slug: "beta" }],
]);

test("summarizeAnalyticsDocs: GMV, заказы, средний чек, воронка", () => {
  const rows: AnalyticsRow[] = [
    { storeId: "s1", date: "2026-06-01", data: { views: 10, purchases: 2, revenue: 4000 } },
    { storeId: "s1", date: "2026-06-02", data: { views: 5, addToCarts: 3, purchases: 1, revenue: 2000 } },
    { storeId: "s2", date: "2026-06-01", data: { views: 8, purchases: 1, revenue: 5000, searches: 4 } },
  ];
  const r = summarizeAnalyticsDocs(rows, stores);
  assert.equal(r.gmv, 11000);
  assert.equal(r.orders, 4);
  assert.equal(r.avgCheck, 2750); // 11000/4
  assert.equal(r.searches, 4);
  assert.equal(r.funnel.views, 23);
  assert.equal(r.funnel.addToCarts, 3);
  assert.equal(r.funnel.purchases, 4);
});

test("summarizeAnalyticsDocs: топ-магазины по GMV с метаданными", () => {
  const rows: AnalyticsRow[] = [
    { storeId: "s1", date: "2026-06-01", data: { revenue: 4000, purchases: 2 } },
    { storeId: "s1", date: "2026-06-02", data: { revenue: 2000, purchases: 1 } },
    { storeId: "s2", date: "2026-06-01", data: { revenue: 5000, purchases: 1 } },
  ];
  const top = summarizeAnalyticsDocs(rows, stores).topStores;
  // s1: 6000 > s2: 5000
  assert.deepEqual(top.map((s) => [s.storeId, s.name, s.gmv, s.orders]), [
    ["s1", "Alpha", 6000, 3],
    ["s2", "Beta", 5000, 1],
  ]);
});

test("summarizeAnalyticsDocs: дневной тренд отсортирован по дате", () => {
  const rows: AnalyticsRow[] = [
    { storeId: "s1", date: "2026-06-02", data: { revenue: 2000, purchases: 1 } },
    { storeId: "s2", date: "2026-06-01", data: { revenue: 5000, purchases: 1 } },
  ];
  const daily = summarizeAnalyticsDocs(rows, stores).daily;
  assert.deepEqual(daily, [
    { date: "2026-06-01", gmv: 5000, orders: 1 },
    { date: "2026-06-02", gmv: 2000, orders: 1 },
  ]);
});

test("summarizeAnalyticsDocs: пустой ввод — нули без падений", () => {
  const r = summarizeAnalyticsDocs([], stores);
  assert.equal(r.gmv, 0);
  assert.equal(r.orders, 0);
  assert.equal(r.avgCheck, 0);
  assert.deepEqual(r.topStores, []);
  assert.deepEqual(r.daily, []);
});
