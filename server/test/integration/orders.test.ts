import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import { createProduct } from "../../src/services/products.ts";
import { createPromocode } from "../../src/services/promocodes.ts";
import {
  cancelOrderByBuyer,
  createOrder,
  getOrder,
  listMyOrders,
  listOrders,
  updateOrderStatus,
} from "../../src/services/orders.ts";
import { renderInvoice } from "../../src/services/invoice.ts";
import type { ProductCreate } from "../../src/schemas/product.ts";

/** Интеграционные тесты ядра заказов (FR-B05/A04/B06) против эмулятора. */

const STORE_ID = "orders-store";
const BUYER = "buyer-1";
const ids: Record<string, string> = {};

function product(name: string, price: number, extra: Partial<ProductCreate> = {}): ProductCreate {
  return {
    name,
    description: "",
    price,
    originalPrice: undefined,
    images: [],
    category: "misc",
    tags: [],
    variants: [],
    status: "active",
    sku: undefined,
    barcode: undefined,
    ...extra,
  } as ProductCreate;
}

function checkout(overrides: Record<string, unknown> = {}) {
  return {
    storeId: STORE_ID,
    items: [{ productId: ids.simple!, qty: 2, variant: undefined }],
    promoCode: undefined,
    delivery: { method: "pickup" as const, address: undefined },
    idempotencyKey: `key-${Math.random().toString(36).slice(2, 12)}`,
    customerEmail: undefined,
    ...overrides,
  };
}

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "orders",
    name: "Orders",
    ownerUid: "owner-orders",
    currency: "USD",
    plan: "enterprise",
    isPublic: true,
    deliveryCost: 700,
  });
  // Товар без вариантов: сток 10
  const simple = await createProduct(STORE_ID, product("Кеды", 5000));
  await db()
    .collection("stores").doc(STORE_ID).collection("products").doc(simple.id)
    .update({ totalStock: 10 });
  ids.simple = simple.id;
  // Товар с вариантами
  const varProduct = await createProduct(
    STORE_ID,
    product("Футболка", 2000, { variants: [{ size: "M", stock: 3 }, { size: "L", stock: 1 }] }),
  );
  ids.variant = varProduct.id;
  // Промокод 10%
  await createPromocode(STORE_ID, {
    code: "TEN",
    type: "percent",
    value: 10,
    minAmount: 0,
    startsAt: null,
    expiresAt: null,
    usageLimit: 100,
    scope: undefined,
    active: true,
  });
});

test("чекаут: серверные цены, списание стока, промокод, доставка", async () => {
  const { order, replay } = await createOrder(BUYER, "buyer@example.com", checkout({
    items: [
      { productId: ids.simple!, qty: 2, variant: undefined },
      { productId: ids.variant!, qty: 1, variant: { size: "M" } },
    ],
    promoCode: "TEN",
    delivery: { method: "courier" as const, address: "ул. Тестовая, 1" },
  }) as Parameters<typeof createOrder>[2]);

  assert.equal(replay, false);
  assert.equal(order.status, "NEW");
  // subtotal = 2*5000 + 1*2000 = 12000; скидка 10% = 1200; курьер 700
  assert.equal(order.subtotal, 12000);
  assert.equal(order.discount, 1200);
  assert.equal(order.delivery.cost, 700);
  assert.equal(order.total, 11500);
  assert.equal(order.payment.method, "deferred");

  // Сток списан
  const simpleSnap = await db()
    .collection("stores").doc(STORE_ID).collection("products").doc(ids.simple!).get();
  assert.equal(simpleSnap.data()?.["totalStock"], 8);
  const varSnap = await db()
    .collection("stores").doc(STORE_ID).collection("products").doc(ids.variant!).get();
  assert.equal(varSnap.data()?.["totalStock"], 3); // 4 - 1

  // Промокод учтён
  const promoSnap = await db()
    .collection("stores").doc(STORE_ID).collection("promocodes").doc("TEN").get();
  assert.equal(promoSnap.data()?.["usedCount"], 1);
  ids.order1 = order.id;
});

test("идемпотентность: повтор ключа → тот же заказ, без двойного списания", async () => {
  const key = "key-idempotent-1";
  const first = await createOrder(BUYER, "b@e.com", checkout({ idempotencyKey: key }) as Parameters<typeof createOrder>[2]);
  const second = await createOrder(BUYER, "b@e.com", checkout({ idempotencyKey: key }) as Parameters<typeof createOrder>[2]);
  assert.equal(first.replay, false);
  assert.equal(second.replay, true);
  assert.equal(first.order.id, second.order.id);

  const snap = await db()
    .collection("stores").doc(STORE_ID).collection("products").doc(ids.simple!).get();
  assert.equal(snap.data()?.["totalStock"], 6); // 8 - 2, списано один раз
});

test("OUT_OF_STOCK: больше остатка — отказ без частичного списания", async () => {
  await assert.rejects(
    () => createOrder(BUYER, "b@e.com", checkout({
      items: [{ productId: ids.variant!, qty: 5, variant: { size: "L" } }], // остаток 1
    }) as Parameters<typeof createOrder>[2]),
    (err: { code?: string }) => err.code === "OUT_OF_STOCK",
  );
  const snap = await db()
    .collection("stores").doc(STORE_ID).collection("products").doc(ids.variant!).get();
  assert.equal(snap.data()?.["totalStock"], 3); // не изменился
});

test("статусы FR-A04: валидный переход, недопустимый — CONFLICT, trackingNo", async () => {
  const confirmed = await updateOrderStatus(STORE_ID, ids.order1!, "CONFIRMED", undefined);
  assert.equal(confirmed.status, "CONFIRMED");
  await assert.rejects(
    () => updateOrderStatus(STORE_ID, ids.order1!, "DELIVERED", undefined), // прыжок
    (err: { code?: string }) => err.code === "CONFLICT",
  );
  await updateOrderStatus(STORE_ID, ids.order1!, "PROCESSING", undefined);
  const shipped = await updateOrderStatus(STORE_ID, ids.order1!, "SHIPPED", "TRACK-42");
  assert.equal(shipped.delivery.trackingNo, "TRACK-42");

  // Идемпотентность (offline-first): повтор того же статуса — no-op, без ошибки
  const replay = await updateOrderStatus(STORE_ID, ids.order1!, "SHIPPED", undefined);
  assert.equal(replay.status, "SHIPPED");
  assert.equal(replay.delivery.trackingNo, "TRACK-42"); // прежний трек не затёрт
  // но возврат назад (SHIPPED → PROCESSING) — по-прежнему CONFLICT
  await assert.rejects(
    () => updateOrderStatus(STORE_ID, ids.order1!, "PROCESSING", undefined),
    (err: { code?: string }) => err.code === "CONFLICT",
  );
});

test("отмена покупателем FR-B06: ресток, чужой заказ — FORBIDDEN, после отгрузки — CONFLICT", async () => {
  // свой заказ в NEW
  const { order } = await createOrder(BUYER, "b@e.com", checkout({
    items: [{ productId: ids.simple!, qty: 3, variant: undefined }],
  }) as Parameters<typeof createOrder>[2]);
  const before = (await db()
    .collection("stores").doc(STORE_ID).collection("products").doc(ids.simple!).get())
    .data()?.["totalStock"] as number;

  const cancelled = await cancelOrderByBuyer(STORE_ID, order.id, BUYER);
  assert.equal(cancelled.status, "CANCELLED");
  const after = (await db()
    .collection("stores").doc(STORE_ID).collection("products").doc(ids.simple!).get())
    .data()?.["totalStock"] as number;
  assert.equal(after, before + 3); // сток восстановлен

  // чужой заказ
  await assert.rejects(
    () => cancelOrderByBuyer(STORE_ID, ids.order1!, "someone-else"),
    (err: { code?: string }) => err.code === "FORBIDDEN",
  );
  // отгруженный (order1 в SHIPPED) — нельзя
  await assert.rejects(
    () => cancelOrderByBuyer(STORE_ID, ids.order1!, BUYER),
    (err: { code?: string }) => err.code === "CONFLICT",
  );
});

test("списки: владелец видит все, покупатель — свои; карточка читается", async () => {
  const all = await listOrders(STORE_ID, { limit: 50 });
  assert.ok(all.length >= 3);
  // новые сверху (createdAt desc)
  for (let i = 1; i < all.length; i++) {
    assert.ok((all[i - 1]!.createdAt ?? 0) >= (all[i]!.createdAt ?? 0));
  }
  const shipped = await listOrders(STORE_ID, { status: "SHIPPED", limit: 50 });
  assert.ok(shipped.every((o) => o.status === "SHIPPED"));

  const mine = await listMyOrders(STORE_ID, BUYER, 50);
  assert.ok(mine.length >= 3);
  assert.ok(mine.every((o) => o.customerUid === BUYER));

  const one = await getOrder(STORE_ID, ids.order1!);
  assert.equal(one.id, ids.order1);
});

test("фильтры FR-A04: дата, сумма, покупатель", async () => {
  const all = await listOrders(STORE_ID, { limit: 100 });
  assert.ok(all.length >= 1);

  // дата: окно вокруг createdAt первого заказа возвращает его, узкое прошлое — нет
  const ref = all[0]!;
  const at = ref.createdAt!;
  const inWindow = await listOrders(STORE_ID, { from: at - 1000, to: at + 1000, limit: 100 });
  assert.ok(inWindow.some((o) => o.id === ref.id));
  const past = await listOrders(STORE_ID, { to: at - 100000, limit: 100 });
  assert.ok(!past.some((o) => o.id === ref.id));

  // сумма: minTotal выше всех — пусто; maxTotal=0 — пусто (все заказы > 0)
  const maxTotal = Math.max(...all.map((o) => o.total));
  assert.equal((await listOrders(STORE_ID, { minTotal: maxTotal + 1, limit: 100 })).length, 0);
  const withinSum = await listOrders(STORE_ID, { minTotal: 0, maxTotal, limit: 100 });
  assert.equal(withinSum.length, all.length);

  // покупатель: подстрока email покупателя (регистронезависимо) находит его заказы
  const byCustomer = await listOrders(STORE_ID, { customer: "BUYER@", limit: 100 });
  assert.ok(byCustomer.length >= 1);
  assert.ok(byCustomer.every((o) => o.customerEmail.toLowerCase().includes("buyer@")));
  assert.equal((await listOrders(STORE_ID, { customer: "nobody-xyz", limit: 100 })).length, 0);
});

test("инвойс FR-A04: рендер HTML с именем магазина и позициями заказа", async () => {
  const order = await getOrder(STORE_ID, ids.order1!);
  const html = await renderInvoice(STORE_ID, order);
  assert.ok(html.includes("<!DOCTYPE html>"));
  assert.ok(html.includes("Orders")); // имя магазина из стора
  assert.ok(html.includes(`Инвойс №${order.id.slice(0, 8).toUpperCase()}`));
  assert.ok(html.includes(order.items[0]!.name)); // позиция заказа
});

test("PROMO_INVALID: несуществующий промокод — отказ без списания", async () => {
  const before = (await db()
    .collection("stores").doc(STORE_ID).collection("products").doc(ids.simple!).get())
    .data()?.["totalStock"] as number;
  await assert.rejects(
    () => createOrder(BUYER, "b@e.com", checkout({ promoCode: "GHOST" }) as Parameters<typeof createOrder>[2]),
    (err: { code?: string }) => err.code === "PROMO_INVALID",
  );
  const after = (await db()
    .collection("stores").doc(STORE_ID).collection("products").doc(ids.simple!).get())
    .data()?.["totalStock"] as number;
  assert.equal(after, before);
});
