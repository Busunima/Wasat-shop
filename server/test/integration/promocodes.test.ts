import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import {
  createPromocode,
  deletePromocode,
  listPromocodes,
  previewPromo,
  updatePromocode,
} from "../../src/services/promocodes.ts";

/** Интеграционные тесты FR-A06: CRUD промокодов + предпросмотр против эмулятора. */

const STORE_ID = "promo-store";

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "promo",
    name: "Promo",
    ownerUid: "owner-promo",
    currency: "USD",
    isPublic: true,
  });
});

test("createPromocode: round-trip и дубликат-конфликт", async () => {
  const created = await createPromocode(STORE_ID, {
    code: "SALE-10",
    type: "percent",
    value: 10,
    minAmount: 5000,
    startsAt: null,
    expiresAt: "2099-01-01T00:00:00.000Z",
    usageLimit: 100,
    scope: undefined,
    active: true,
  });
  assert.equal(created.code, "SALE-10");
  assert.equal(created.type, "percent");
  assert.equal(created.usedCount, 0);
  assert.equal(created.expiresAt, "2099-01-01T00:00:00.000Z");

  await assert.rejects(
    () =>
      createPromocode(STORE_ID, {
        code: "SALE-10",
        type: "fixed",
        value: 100,
        minAmount: 0,
        startsAt: null,
        expiresAt: null,
        usageLimit: null,
        scope: undefined,
        active: true,
      }),
    /уже есть/,
  );
});

test("previewPromo: валидный, мин. сумма и несуществующий", async () => {
  // достаточная сумма → 10% от 8000 = 800
  const ok = await previewPromo(STORE_ID, "SALE-10", {
    subtotal: 8000,
    itemProductIds: ["p1"],
    itemCategories: [],
  });
  assert.equal(ok.valid, true);
  assert.equal(ok.discount, 800);

  // ниже minAmount (5000)
  const low = await previewPromo(STORE_ID, "SALE-10", {
    subtotal: 1000,
    itemProductIds: ["p1"],
    itemCategories: [],
  });
  assert.equal(low.valid, false);

  // несуществующий код → valid=false, не исключение
  const missing = await previewPromo(STORE_ID, "NOPE", {
    subtotal: 8000,
    itemProductIds: [],
    itemCategories: [],
  });
  assert.equal(missing.valid, false);
  assert.equal(missing.reason, "Промокод не найден");
});

test("updatePromocode: частичный патч (деактивация) и list", async () => {
  const updated = await updatePromocode(STORE_ID, "SALE-10", { active: false });
  assert.equal(updated.active, false);
  // value не трогали
  assert.equal(updated.value, 10);

  const preview = await previewPromo(STORE_ID, "SALE-10", {
    subtotal: 8000,
    itemProductIds: ["p1"],
    itemCategories: [],
  });
  assert.equal(preview.valid, false); // неактивен

  const list = await listPromocodes(STORE_ID);
  assert.ok(list.some((p) => p.code === "SALE-10"));
});

test("deletePromocode: удаление и 404 на повтор", async () => {
  await deletePromocode(STORE_ID, "SALE-10");
  await assert.rejects(() => deletePromocode(STORE_ID, "SALE-10"), /не найден/);
});
