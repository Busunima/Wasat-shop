import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import { getStoreInfo, resolveSlug, updateStore } from "../../src/services/stores.ts";
import { ApiError } from "../../src/middleware/errorHandler.ts";
import { storeUpdateSchema } from "../../src/schemas/store.ts";

/**
 * Интеграционные тесты FR-A01 (настройки) и FR-B01 (резолв slug)
 * против эмулятора Firestore. Запуск: npm run test:integration.
 */

const STORE_ID = "settings-store";
const SLUG = "settings-shop";

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: SLUG,
    name: "Settings Shop",
    ownerUid: "owner-5",
    currency: "USD",
    isPublic: false,
    isBlocked: false,
  });
  await db().collection("slugs").doc(SLUG).set({ storeId: STORE_ID });
});

test("updateStore: публикация витрины + тема + контакты; partial-PATCH", async () => {
  const updated = await updateStore(
    STORE_ID,
    storeUpdateSchema.parse({
      isPublic: true,
      theme: { primary: "#2D4A7C", secondary: "#5B6B82" },
      contact: { email: "shop@example.com", phone: "+1234567" },
      deliveryCost: 700,
    }),
  );
  assert.equal(updated.isPublic, true);
  assert.equal(updated.theme?.primary, "#2D4A7C");
  assert.equal(updated.deliveryCost, 700);

  // PATCH другого поля не трогает уже сохранённые
  const renamed = await updateStore(STORE_ID, storeUpdateSchema.parse({ name: "Renamed" }));
  assert.equal(renamed.name, "Renamed");
  assert.equal(renamed.theme?.primary, "#2D4A7C");
  assert.equal(renamed.isPublic, true);

  // "" очищает logoUrl
  await updateStore(STORE_ID, storeUpdateSchema.parse({ logoUrl: "https://cdn.test/l.png" }));
  const cleared = await updateStore(STORE_ID, storeUpdateSchema.parse({ logoUrl: "" }));
  assert.equal(cleared.logoUrl, null);
});

test("resolveSlug: публичный магазин резолвится, после снятия публикации — 404", async () => {
  await updateStore(STORE_ID, storeUpdateSchema.parse({ isPublic: true }));
  const info = await resolveSlug(SLUG);
  assert.equal(info.storeId, STORE_ID);
  assert.equal(info.slug, SLUG);

  await updateStore(STORE_ID, storeUpdateSchema.parse({ isPublic: false }));
  await assert.rejects(
    () => resolveSlug(SLUG),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );
  // владелец свой непубличный магазин видит напрямую
  const asOwner = await getStoreInfo(STORE_ID, true);
  assert.equal(asOwner.isPublic, false);
});

test("resolveSlug: несуществующий slug — NOT_FOUND; updateStore несуществующего — NOT_FOUND", async () => {
  await assert.rejects(
    () => resolveSlug("no-such-slug"),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );
  await assert.rejects(
    () => updateStore("no-such-store", storeUpdateSchema.parse({ name: "X" })),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );
});
