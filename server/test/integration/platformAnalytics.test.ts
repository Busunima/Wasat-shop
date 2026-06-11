import assert from "node:assert/strict";
import { before, test } from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { db } from "../../src/lib/firebase.ts";
import { getPlatformAnalytics } from "../../src/services/platformAnalytics.ts";

/**
 * Интеграционные тесты FR-S04: глобальная аналитика. Сидим уникальную дату
 * (вне «сегодня»), чтобы агрегаты других тестов не попадали в диапазон.
 */

const DAY = "2025-03-15";

async function seedStore(id: string, fields: Record<string, unknown>, daily: Record<string, number>) {
  await db().collection("stores").doc(id).set({ id, slug: id, ...fields });
  await db().collection("stores").doc(id).collection("analytics").doc(DAY).set(daily);
}

before(async () => {
  await seedStore(
    "pa-alpha",
    { name: "Alpha", plan: "pro", isPublic: true, isBlocked: false },
    { views: 100, purchases: 3, revenue: 30000, searches: 5 },
  );
  await seedStore(
    "pa-beta",
    { name: "Beta", plan: "free", isPublic: false, isBlocked: true },
    { views: 40, purchases: 1, revenue: 5000 },
  );
  // Активные пользователи (MAU) — со свежим lastActiveAt.
  await db().collection("users").doc("pa-u1").set({ lastActiveAt: Timestamp.now() });
  await db().collection("users").doc("pa-u2").set({ lastActiveAt: Timestamp.now() });
});

test("getPlatformAnalytics: GMV, заказы, средний чек за период", async () => {
  const r = await getPlatformAnalytics({ from: DAY, to: DAY });
  assert.equal(r.gmv, 35000);
  assert.equal(r.orders, 4);
  assert.equal(r.avgCheck, 8750); // 35000/4
  assert.equal(r.searches, 5);
});

test("getPlatformAnalytics: топ-магазины по GMV в диапазоне", async () => {
  const r = await getPlatformAnalytics({ from: DAY, to: DAY });
  // только наши два магазина имеют аналитику на эту дату
  assert.deepEqual(
    r.topStores.map((s) => [s.storeId, s.gmv]),
    [
      ["pa-alpha", 30000],
      ["pa-beta", 5000],
    ],
  );
  assert.deepEqual(r.daily, [{ date: DAY, gmv: 35000, orders: 4 }]);
});

test("getPlatformAnalytics: состав магазинов и тарифы", async () => {
  const r = await getPlatformAnalytics({ from: DAY, to: DAY });
  assert.ok(r.stores.total >= 2);
  assert.ok(r.stores.blocked >= 1); // pa-beta заблокирован
  assert.ok(r.stores.byPlan.pro >= 1);
  assert.ok(r.stores.byPlan.free >= 1);
});

test("getPlatformAnalytics: MAU по lastActiveAt", async () => {
  const r = await getPlatformAnalytics({ from: DAY, to: DAY });
  assert.ok(r.mau >= 2);
});

test("getPlatformAnalytics: пустой период вне дат — нулевой GMV", async () => {
  const r = await getPlatformAnalytics({ from: "2020-01-01", to: "2020-01-02" });
  assert.equal(r.gmv, 0);
  assert.equal(r.orders, 0);
  assert.deepEqual(r.topStores, []);
});
