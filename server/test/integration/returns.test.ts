import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import { createProduct, getProduct } from "../../src/services/products.ts";
import {
  createReturn,
  getReturn,
  listMyReturns,
  listReturns,
  receiveReturn,
  refundReturn,
  resolveReturn,
} from "../../src/services/returns.ts";
import type { ProductCreate } from "../../src/schemas/product.ts";

/** Интеграционные тесты FR-B09/A11: флоу возврата + order-status + ресток. */

const STORE_ID = "returns-store";
const BUYER = "ret-buyer";
let productId = "";

async function seedOrder(id: string, status: string, items: unknown[]) {
  await db().collection("stores").doc(STORE_ID).collection("orders").doc(id).set({
    id,
    customerUid: BUYER,
    status,
    items,
  });
}

function product(name: string): ProductCreate {
  return {
    name,
    description: "",
    price: 5000,
    originalPrice: undefined,
    images: [],
    category: "misc",
    tags: [],
    variants: [],
    status: "active",
    sku: undefined,
    barcode: undefined,
  } as ProductCreate;
}

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "returns",
    name: "Returns",
    ownerUid: "owner-ret",
    currency: "USD",
    plan: "enterprise",
    isPublic: true,
  });
  const p = await createProduct(STORE_ID, product("Кеды"));
  productId = p.id;
  await db()
    .collection("stores").doc(STORE_ID).collection("products").doc(productId)
    .update({ totalStock: 5 });

  await seedOrder("ord-delivered", "DELIVERED", [{ productId, name: "Кеды", qty: 2, price: 5000, variant: null }]);
  await seedOrder("ord-new", "NEW", [{ productId, name: "Кеды", qty: 1, price: 5000, variant: null }]);
});

test("createReturn: заявка по доставленному → REQUESTED, заказ RETURN_REQUESTED", async () => {
  const ret = await createReturn(STORE_ID, BUYER, {
    orderId: "ord-delivered",
    items: [{ productId, qty: 2 }],
    reason: "не подошёл размер",
  });
  assert.equal(ret.status, "REQUESTED");

  const order = await db()
    .collection("stores").doc(STORE_ID).collection("orders").doc("ord-delivered").get();
  assert.equal(order.data()?.["status"], "RETURN_REQUESTED");
  (globalThis as Record<string, unknown>)["retId"] = ret.id;
});

test("createReturn: недоставленный заказ → CONFLICT; чужой → NOT_FOUND", async () => {
  await assert.rejects(
    () => createReturn(STORE_ID, BUYER, { orderId: "ord-new", items: [{ productId, qty: 1 }], reason: "брак" }),
    (e: { code?: string }) => e.code === "CONFLICT",
  );
  await assert.rejects(
    () => createReturn(STORE_ID, "stranger", { orderId: "ord-delivered", items: [{ productId, qty: 1 }], reason: "брак" }),
    (e: { code?: string }) => e.code === "NOT_FOUND",
  );
});

test("полный флоу: approve → receive (ресток) → refund (deferred) + статусы заказа", async () => {
  const retId = (globalThis as Record<string, unknown>)["retId"] as string;
  const stockBefore = (await getProduct(STORE_ID, productId, true)).totalStock;

  const approved = await resolveReturn(STORE_ID, retId, "approve", "ок");
  assert.equal(approved.status, "APPROVED");

  const received = await receiveReturn(STORE_ID, retId);
  assert.equal(received.status, "RECEIVED");
  // ресток: вернулось 2 шт
  assert.equal((await getProduct(STORE_ID, productId, true)).totalStock, stockBefore + 2);
  assert.equal(
    (await db().collection("stores").doc(STORE_ID).collection("orders").doc("ord-delivered").get()).data()?.["status"],
    "RETURNED",
  );

  const refunded = await refundReturn(STORE_ID, retId);
  assert.equal(refunded.status, "REFUNDED");
  assert.equal(refunded.refundAmount, 10000); // 2 × 5000
  assert.equal(refunded.refundDeferred, true); // без Stripe-ключа
  assert.equal(
    (await db().collection("stores").doc(STORE_ID).collection("orders").doc("ord-delivered").get()).data()?.["status"],
    "REFUNDED",
  );
});

test("переходы: недопустимый порядок → CONFLICT", async () => {
  const retId = (globalThis as Record<string, unknown>)["retId"] as string;
  // уже REFUNDED — повторный refund/receive нельзя
  await assert.rejects(
    () => receiveReturn(STORE_ID, retId),
    (e: { code?: string }) => e.code === "CONFLICT",
  );
});

test("reject: возврат REJECTED, заказ обратно в COMPLETED", async () => {
  await seedOrder("ord-d2", "COMPLETED", [{ productId, name: "Кеды", qty: 1, price: 5000, variant: null }]);
  const ret = await createReturn(STORE_ID, BUYER, {
    orderId: "ord-d2",
    items: [{ productId, qty: 1 }],
    reason: "передумал",
  });
  const rejected = await resolveReturn(STORE_ID, ret.id, "reject", "вне срока");
  assert.equal(rejected.status, "REJECTED");
  assert.equal(rejected.comment, "вне срока");
  assert.equal(
    (await db().collection("stores").doc(STORE_ID).collection("orders").doc("ord-d2").get()).data()?.["status"],
    "COMPLETED",
  );
});

test("списки и карточка", async () => {
  const all = await listReturns(STORE_ID, undefined, 50);
  assert.ok(all.length >= 2);
  const refunded = await listReturns(STORE_ID, "REFUNDED", 50);
  assert.ok(refunded.every((r) => r.status === "REFUNDED"));
  const mine = await listMyReturns(STORE_ID, BUYER, 50);
  assert.ok(mine.every((r) => r.customerUid === BUYER));
  const one = await getReturn(STORE_ID, (globalThis as Record<string, unknown>)["retId"] as string);
  assert.equal(one.status, "REFUNDED");
});
