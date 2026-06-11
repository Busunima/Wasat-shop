import assert from "node:assert/strict";
import { test } from "node:test";
import {
  ALLOWED_TRANSITIONS,
  canTransition,
  checkoutSchema,
  computeTotals,
} from "../../src/schemas/order.ts";
import { ORDER_STATUS } from "../../src/schemas/orderStatus.ts";

test("canTransition: happy path и запреты", () => {
  assert.equal(canTransition("NEW", "CONFIRMED"), true);
  assert.equal(canTransition("CONFIRMED", "PROCESSING"), true);
  assert.equal(canTransition("PROCESSING", "SHIPPED"), true);
  assert.equal(canTransition("SHIPPED", "DELIVERED"), true);
  assert.equal(canTransition("DELIVERED", "COMPLETED"), true);
  // запреты
  assert.equal(canTransition("NEW", "SHIPPED"), false); // прыжок через статусы
  assert.equal(canTransition("SHIPPED", "CANCELLED"), false); // отгружен — не отменить
  assert.equal(canTransition("CANCELLED", "NEW"), false); // терминальный
  assert.equal(canTransition("REFUNDED", "COMPLETED"), false); // терминальный
});

test("ALLOWED_TRANSITIONS: покрывает все статусы канонического enum", () => {
  for (const status of ORDER_STATUS) {
    assert.ok(status in ALLOWED_TRANSITIONS, `нет переходов для ${status}`);
  }
});

test("computeTotals: доставка и скидка", () => {
  // самовывоз — доставка 0
  assert.deepEqual(
    computeTotals({ subtotal: 10000, discount: 0, freeShipping: false, method: "pickup", storeDeliveryCost: 500 }),
    { delivery: 0, total: 10000 },
  );
  // курьер — стоимость магазина
  assert.deepEqual(
    computeTotals({ subtotal: 10000, discount: 1000, freeShipping: false, method: "courier", storeDeliveryCost: 500 }),
    { delivery: 500, total: 9500 },
  );
  // free_shipping промо обнуляет доставку
  assert.deepEqual(
    computeTotals({ subtotal: 10000, discount: 0, freeShipping: true, method: "courier", storeDeliveryCost: 500 }),
    { delivery: 0, total: 10000 },
  );
  // скидка больше суммы — total не уходит в минус
  assert.equal(
    computeTotals({ subtotal: 100, discount: 500, freeShipping: false, method: "pickup", storeDeliveryCost: null }).total,
    0,
  );
});

test("checkoutSchema: границы и адрес для курьера", () => {
  const valid = checkoutSchema.parse({
    storeId: "s1",
    items: [{ productId: "p1", qty: 2 }],
    delivery: { method: "pickup" },
    idempotencyKey: "key-12345678",
  });
  assert.equal(valid.items[0]!.qty, 2);

  // промокод нормализуется к верхнему регистру
  assert.equal(
    checkoutSchema.parse({
      storeId: "s1",
      items: [{ productId: "p1", qty: 1 }],
      promoCode: " sale-10 ",
      delivery: { method: "pickup" },
      idempotencyKey: "key-12345678",
    }).promoCode,
    "SALE-10",
  );

  // курьер без адреса — ошибка
  assert.throws(() =>
    checkoutSchema.parse({
      storeId: "s1",
      items: [{ productId: "p1", qty: 1 }],
      delivery: { method: "courier" },
      idempotencyKey: "key-12345678",
    }),
  );
  // пустая корзина / короткий ключ
  assert.throws(() =>
    checkoutSchema.parse({
      storeId: "s1",
      items: [],
      delivery: { method: "pickup" },
      idempotencyKey: "key-12345678",
    }),
  );
  assert.throws(() =>
    checkoutSchema.parse({
      storeId: "s1",
      items: [{ productId: "p1", qty: 1 }],
      delivery: { method: "pickup" },
      idempotencyKey: "short",
    }),
  );
});
