import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import { createProduct } from "../../src/services/products.ts";
import { getPlanUsage } from "../../src/services/stores.ts";
import { addStaff } from "../../src/services/staff.ts";

/** Интеграционные тесты FR-S03: энфорсмент лимитов тарифа против эмулятора. */

const STORE_ID = "plan-store";

function product(name: string) {
  return {
    name,
    description: "",
    price: 1000,
    originalPrice: undefined,
    images: [],
    category: undefined,
    tags: [],
    variants: [],
    status: "active" as const,
    sku: undefined,
    barcode: undefined,
  };
}

before(async () => {
  // Тариф free: maxProducts 25, maxStaff 0 — переопределим на маленький через basic? Нет,
  // free даёт 25 товаров. Чтобы быстро упереться, выставим кастомный план не получится.
  // Используем free и проверим запрет сотрудников + счётчики.
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "plan",
    name: "Plan",
    ownerUid: "owner-plan",
    currency: "USD",
    plan: "free",
    isPublic: true,
  });
});

test("getPlanUsage: тариф, лимиты и нулевое использование", async () => {
  const usage = await getPlanUsage(STORE_ID);
  assert.equal(usage.plan, "free");
  assert.equal(usage.limits.maxProducts, 25);
  assert.equal(usage.limits.maxStaff, 0);
  assert.equal(usage.usage.products, 0);
  assert.equal(usage.usage.staff, 0);
});

test("createProduct: учитывается в использовании тарифа", async () => {
  await createProduct(STORE_ID, product("Товар 1"));
  const usage = await getPlanUsage(STORE_ID);
  assert.equal(usage.usage.products, 1);
});

test("addStaff: на free сотрудники запрещены (лимит 0)", async () => {
  await assert.rejects(
    () => addStaff(STORE_ID, "owner-plan", "anybody@example.com", "staff"),
    /лимит сотрудников/i,
  );
});

test("createProduct: лимит товаров enterprise (null) не ограничивает", async () => {
  await db().collection("stores").doc(STORE_ID).update({ plan: "enterprise" });
  const created = await createProduct(STORE_ID, product("Товар 2"));
  assert.equal(created.name, "Товар 2");
  const usage = await getPlanUsage(STORE_ID);
  assert.equal(usage.plan, "enterprise");
  assert.equal(usage.limits.maxProducts, null);
});
