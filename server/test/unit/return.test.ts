import assert from "node:assert/strict";
import { test } from "node:test";
import {
  RETURN_STATUS,
  RETURN_TRANSITIONS,
  canTransitionReturn,
  computeRefundAmount,
  returnCreateSchema,
} from "../../src/schemas/return.ts";

test("canTransitionReturn: легальные и терминальные", () => {
  assert.equal(canTransitionReturn("REQUESTED", "APPROVED"), true);
  assert.equal(canTransitionReturn("REQUESTED", "REJECTED"), true);
  assert.equal(canTransitionReturn("APPROVED", "RECEIVED"), true);
  assert.equal(canTransitionReturn("RECEIVED", "REFUNDED"), true);
  // запреты
  assert.equal(canTransitionReturn("REQUESTED", "RECEIVED"), false); // прыжок
  assert.equal(canTransitionReturn("REJECTED", "APPROVED"), false); // терминальный
  assert.equal(canTransitionReturn("REFUNDED", "RECEIVED"), false);
});

test("RETURN_TRANSITIONS покрывает все статусы enum", () => {
  for (const s of RETURN_STATUS) assert.ok(s in RETURN_TRANSITIONS);
});

test("computeRefundAmount: по позициям заказа, qty ограничен заказом", () => {
  const orderItems = [
    { productId: "p1", price: 5000, qty: 2 },
    { productId: "p2", price: 2000, qty: 1 },
  ];
  // вернуть 1×p1 + 1×p2 = 5000 + 2000
  assert.equal(computeRefundAmount(orderItems, [{ productId: "p1", qty: 1 }, { productId: "p2", qty: 1 }]), 7000);
  // вернуть 2×p1 = 10000
  assert.equal(computeRefundAmount(orderItems, [{ productId: "p1", qty: 2 }]), 10000);
  // qty больше заказанного — обрезается до заказа
  assert.equal(computeRefundAmount(orderItems, [{ productId: "p1", qty: 9 }]), 10000);
  // несуществующий товар игнорируется
  assert.equal(computeRefundAmount(orderItems, [{ productId: "ghost", qty: 1 }]), 0);
});

test("returnCreateSchema: позиции и причина обязательны", () => {
  const ok = returnCreateSchema.parse({
    orderId: "o1",
    items: [{ productId: "p1", qty: 1 }],
    reason: "брак",
  });
  assert.equal(ok.items.length, 1);
  assert.throws(() => returnCreateSchema.parse({ orderId: "o1", items: [], reason: "брак" }));
  assert.throws(() =>
    returnCreateSchema.parse({ orderId: "o1", items: [{ productId: "p1", qty: 1 }], reason: "x" }),
  );
});
