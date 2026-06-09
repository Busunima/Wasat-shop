import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { auth, db } from "../../src/lib/firebase.ts";
import { createStore } from "../../src/services/stores.ts";
import { ApiError } from "../../src/middleware/errorHandler.ts";

/**
 * Интеграционные тесты createStore против эмуляторов Firestore + Auth.
 * Запуск только через: npm run test:integration (firebase emulators:exec ...).
 * Эмулятор-хосты приходят из окружения, выставляемого firebase emulators:exec.
 */

const UID = "owner-uid-1";
const EMAIL = "owner@example.com";

before(async () => {
  // setCustomUserClaims требует существующего пользователя в Auth-эмуляторе.
  await auth().createUser({ uid: UID, email: EMAIL });
});

after(async () => {
  // Чистим за собой, чтобы повторный прогон был идемпотентным.
  await auth()
    .deleteUser(UID)
    .catch(() => undefined);
});

test("createStore создаёт store + slug-индекс + членство и выставляет Custom Claim", async () => {
  const result = await createStore(UID, EMAIL, {
    name: "Sneaker Hub",
    slug: "sneaker-hub",
    currency: "USD",
  });

  assert.ok(result.storeId);
  assert.equal(result.slug, "sneaker-hub");

  const storeSnap = await db().collection("stores").doc(result.storeId).get();
  assert.equal(storeSnap.exists, true);
  const store = storeSnap.data()!;
  assert.equal(store["ownerUid"], UID);
  assert.equal(store["ownerEmail"], EMAIL);
  assert.equal(store["plan"], "free");
  assert.equal(store["isPublic"], false);
  assert.equal(store["currency"], "USD");

  const slugSnap = await db().collection("slugs").doc("sneaker-hub").get();
  assert.equal(slugSnap.data()?.["storeId"], result.storeId);

  const userSnap = await db().collection("users").doc(UID).get();
  const stores = (userSnap.data()?.["stores"] ?? []) as Array<{ storeId: string; role: string }>;
  assert.ok(stores.some((s) => s.storeId === result.storeId && s.role === "owner"));

  const user = await auth().getUser(UID);
  assert.equal(user.customClaims?.["storeId"], result.storeId);
  assert.equal(user.customClaims?.["role"], "owner");
});

test("createStore отвергает дублирующийся slug (CONFLICT)", async () => {
  await createStore(UID, EMAIL, { name: "First", slug: "dup-slug", currency: "EUR" });

  await assert.rejects(
    () => createStore(UID, EMAIL, { name: "Second", slug: "dup-slug", currency: "EUR" }),
    (err: unknown) => err instanceof ApiError && err.code === "CONFLICT",
  );
});
