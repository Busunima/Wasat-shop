import assert from "node:assert/strict";
import { test } from "node:test";
import {
  applyPromo,
  promoCreateSchema,
  promoUpdateSchema,
  type PromoEvaluable,
  type PromoContext,
} from "../../src/schemas/promocode.ts";

const NOW = Date.UTC(2026, 5, 11); // 2026-06-11

function promo(overrides: Partial<PromoEvaluable> = {}): PromoEvaluable {
  return {
    type: "fixed",
    value: 500,
    minAmount: 0,
    startsAtMs: null,
    expiresAtMs: null,
    usageLimit: null,
    usedCount: 0,
    scope: null,
    active: true,
    ...overrides,
  };
}

function ctx(overrides: Partial<PromoContext> = {}): PromoContext {
  return {
    subtotal: 10000,
    itemProductIds: ["p1", "p2"],
    itemCategories: ["shoes"],
    nowMs: NOW,
    ...overrides,
  };
}

test("applyPromo fixed: скидка = min(value, subtotal)", () => {
  assert.deepEqual(applyPromo(promo({ value: 500 }), ctx()), {
    valid: true,
    discount: 500,
    freeShipping: false,
  });
  // value больше суммы — обрезается до subtotal
  assert.equal(applyPromo(promo({ value: 99999 }), ctx({ subtotal: 3000 })).discount, 3000);
});

test("applyPromo percent: floor(subtotal * value / 100)", () => {
  assert.equal(applyPromo(promo({ type: "percent", value: 10 }), ctx({ subtotal: 10000 })).discount, 1000);
  // округление вниз
  assert.equal(applyPromo(promo({ type: "percent", value: 33 }), ctx({ subtotal: 999 })).discount, 329);
});

test("applyPromo free_shipping: discount=0, freeShipping=true", () => {
  assert.deepEqual(applyPromo(promo({ type: "free_shipping" }), ctx()), {
    valid: true,
    discount: 0,
    freeShipping: true,
  });
});

test("applyPromo: неактивный код отклоняется", () => {
  const r = applyPromo(promo({ active: false }), ctx());
  assert.equal(r.valid, false);
  assert.equal(r.discount, 0);
});

test("applyPromo: ещё не действует / истёк", () => {
  assert.equal(applyPromo(promo({ startsAtMs: NOW + 86_400_000 }), ctx()).valid, false);
  assert.equal(applyPromo(promo({ expiresAtMs: NOW - 1 }), ctx()).valid, false);
  // в окне действия — ок
  assert.equal(
    applyPromo(promo({ startsAtMs: NOW - 1, expiresAtMs: NOW + 1 }), ctx()).valid,
    true,
  );
});

test("applyPromo: исчерпан лимит применений", () => {
  assert.equal(applyPromo(promo({ usageLimit: 5, usedCount: 5 }), ctx()).valid, false);
  assert.equal(applyPromo(promo({ usageLimit: 5, usedCount: 4 }), ctx()).valid, true);
});

test("applyPromo: минимальная сумма корзины", () => {
  assert.equal(applyPromo(promo({ minAmount: 20000 }), ctx({ subtotal: 10000 })).valid, false);
  assert.equal(applyPromo(promo({ minAmount: 10000 }), ctx({ subtotal: 10000 })).valid, true);
});

test("applyPromo: scope по товарам и категориям", () => {
  // совпадение по товару
  assert.equal(
    applyPromo(promo({ scope: { productIds: ["p2"] } }), ctx({ itemProductIds: ["p2"] })).valid,
    true,
  );
  // нет совпадения
  assert.equal(
    applyPromo(promo({ scope: { productIds: ["pX"] } }), ctx({ itemProductIds: ["p1"] })).valid,
    false,
  );
  // совпадение по категории
  assert.equal(
    applyPromo(promo({ scope: { categories: ["shoes"] } }), ctx({ itemCategories: ["shoes"] })).valid,
    true,
  );
  // пустой scope = применим всегда
  assert.equal(applyPromo(promo({ scope: {} }), ctx()).valid, true);
});

test("promoCreateSchema: код, percent 1..100, дефолты", () => {
  const ok = promoCreateSchema.parse({ code: "SALE-10", type: "percent", value: 10 });
  assert.equal(ok.active, true);
  assert.equal(ok.minAmount, 0);
  // невалидный код
  assert.throws(() => promoCreateSchema.parse({ code: "ab", type: "fixed", value: 100 }));
  assert.throws(() => promoCreateSchema.parse({ code: "sale10", type: "fixed", value: 100 }));
  // percent вне диапазона
  assert.throws(() => promoCreateSchema.parse({ code: "P", type: "percent", value: 0 }));
  assert.throws(() => promoCreateSchema.parse({ code: "BIG100", type: "percent", value: 101 }));
});

test("promoUpdateSchema: частичный, без поля code", () => {
  const patch = promoUpdateSchema.parse({ active: false });
  assert.deepEqual(patch, { active: false });
  // code не принимается на обновлении (omit) — лишнее поле молча игнорируется
  assert.equal("code" in promoUpdateSchema.parse({ code: "X", value: 5 }), false);
});
