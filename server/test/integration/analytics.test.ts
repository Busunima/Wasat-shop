import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import { getAnalytics, recordCustomerType, recordEvent } from "../../src/services/analytics.ts";
import { todayUtc } from "../../src/schemas/analytics.ts";

/** Интеграционные тесты §16/FR-A05: события → агрегат → воронка. */

const STORE_ID = "analytics-store";

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "analytics",
    name: "Analytics",
    ownerUid: "owner-an",
    currency: "USD",
    isPublic: true,
  });
});

test("recordEvent + getAnalytics: воронка, выручка, средний чек, топ-товары", async () => {
  // 4 просмотра (p1 x3, p2 x1), 2 в корзину, 1 чекаут, 1 покупка на 9990
  await recordEvent(STORE_ID, { type: "product_view", productId: "p1" });
  await recordEvent(STORE_ID, { type: "product_view", productId: "p1" });
  await recordEvent(STORE_ID, { type: "product_view", productId: "p1" });
  await recordEvent(STORE_ID, { type: "product_view", productId: "p2" });
  await recordEvent(STORE_ID, { type: "add_to_cart", productId: "p1" });
  await recordEvent(STORE_ID, { type: "add_to_cart", productId: "p2" });
  await recordEvent(STORE_ID, { type: "begin_checkout" });
  await recordEvent(STORE_ID, { type: "purchase", value: 9990 });
  await recordEvent(STORE_ID, { type: "search", query: "кеды" });
  // return_requested принимается и считается (§16), не ломает воронку
  await recordEvent(STORE_ID, { type: "return_requested" });

  const report = await getAnalytics(STORE_ID, {});
  assert.equal(report.funnel.views, 4);
  assert.equal(report.funnel.addToCarts, 2);
  assert.equal(report.funnel.checkouts, 1);
  assert.equal(report.funnel.purchases, 1);
  assert.equal(report.revenue, 9990);
  assert.equal(report.orders, 1);
  assert.equal(report.avgCheck, 9990);
  assert.equal(report.searches, 1);

  // конверсии: view->cart 2/4=50%, cart->order 1/2=50%, view->order 1/4=25%
  assert.equal(report.conversion.viewToCart, 50);
  assert.equal(report.conversion.cartToOrder, 50);
  assert.equal(report.conversion.viewToOrder, 25);

  // топ-товары: p1 (3) перед p2 (1)
  assert.deepEqual(report.topProducts.map((p) => [p.productId, p.views]), [
    ["p1", 3],
    ["p2", 1],
  ]);

  // дневной агрегат содержит сегодняшнюю дату
  assert.ok(report.daily.some((d) => d.date === todayUtc() && d.views === 4));
});

test("getAnalytics: пустой период — нули, без падений", async () => {
  const report = await getAnalytics(STORE_ID, { from: "2020-01-01", to: "2020-01-03" });
  assert.equal(report.funnel.views, 0);
  assert.equal(report.revenue, 0);
  assert.equal(report.avgCheck, 0);
  assert.equal(report.conversion.viewToCart, 0);
  assert.deepEqual(report.topProducts, []);
  assert.equal(report.daily.length, 3);
  assert.deepEqual(report.customers, { new: 0, returning: 0 });
});

test("recordCustomerType: new-vs-returning суммируется в отчёте (FR-A05)", async () => {
  await recordCustomerType(STORE_ID, true);
  await recordCustomerType(STORE_ID, true);
  await recordCustomerType(STORE_ID, false);
  const report = await getAnalytics(STORE_ID, {});
  assert.equal(report.customers.new, 2);
  assert.equal(report.customers.returning, 1);
});
