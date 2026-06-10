import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { after, before, beforeEach, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";

/**
 * Тесты Firestore Security Rules (firestore.rules, ТЗ §4.3).
 * Запуск: npm run test:emulator (firebase emulators:exec --only firestore).
 * FIRESTORE_EMULATOR_HOST выставляет firebase emulators:exec.
 */

const here = dirname(fileURLToPath(import.meta.url));
const rules = readFileSync(join(here, "..", "firestore.rules"), "utf8");

const STORE_ID = "store-1";
const OWNER = "owner-uid";
const STAFF = "staff-uid";
const BUYER = "buyer-uid";
const OUTSIDER = "outsider-uid";

let env: RulesTestEnvironment;

before(async () => {
  const host = process.env["FIRESTORE_EMULATOR_HOST"] ?? "127.0.0.1:8080";
  const [hostname, port] = host.split(":");
  env = await initializeTestEnvironment({
    projectId: "demo-wasat",
    firestore: { rules, host: hostname, port: Number(port) },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  // Сидируем магазин и сотрудника в обход правил.
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "stores", STORE_ID), {
      ownerUid: OWNER,
      isPublic: true,
    });
    await setDoc(doc(db, "stores", STORE_ID, "staff", STAFF), { role: "manager" });
    await setDoc(doc(db, "stores", STORE_ID, "products", "p1"), { name: "Item" });
    await setDoc(doc(db, "stores", STORE_ID, "orders", "o1"), { customerUid: BUYER });
  });
});

test("товары читаются публично (неаутентифицированный)", async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertSucceeds(getDoc(doc(db, "stores", STORE_ID, "products", "p1")));
});

test("владелец может писать товар; посторонний — нет", async () => {
  const owner = env.authenticatedContext(OWNER).firestore();
  await assertSucceeds(setDoc(doc(owner, "stores", STORE_ID, "products", "p2"), { name: "New" }));

  const outsider = env.authenticatedContext(OUTSIDER).firestore();
  await assertFails(setDoc(doc(outsider, "stores", STORE_ID, "products", "p3"), { name: "X" }));
});

test("сотрудник может писать товар", async () => {
  const staff = env.authenticatedContext(STAFF).firestore();
  await assertSucceeds(setDoc(doc(staff, "stores", STORE_ID, "products", "p4"), { name: "Staff" }));
});

test("публичный магазин читается всеми; владелец — тоже", async () => {
  const anon = env.unauthenticatedContext().firestore();
  await assertSucceeds(getDoc(doc(anon, "stores", STORE_ID)));

  const owner = env.authenticatedContext(OWNER).firestore();
  await assertSucceeds(getDoc(doc(owner, "stores", STORE_ID)));
});

test("заказ читается своим покупателем; чужим — нет", async () => {
  const buyer = env.authenticatedContext(BUYER).firestore();
  await assertSucceeds(getDoc(doc(buyer, "stores", STORE_ID, "orders", "o1")));

  const outsider = env.authenticatedContext(OUTSIDER).firestore();
  await assertFails(getDoc(doc(outsider, "stores", STORE_ID, "orders", "o1")));
});

test("клиентская запись в orders запрещена (только Admin SDK)", async () => {
  const owner = env.authenticatedContext(OWNER).firestore();
  await assertFails(setDoc(doc(owner, "stores", STORE_ID, "orders", "o2"), { customerUid: BUYER }));
});

test("клиентская запись в analytics запрещена", async () => {
  const owner = env.authenticatedContext(OWNER).firestore();
  await assertFails(setDoc(doc(owner, "stores", STORE_ID, "analytics", "2026-06-09"), { v: 1 }));
});

test("customers: покупатель пишет свой профиль (корзина), чужой — нет", async () => {
  const buyer = env.authenticatedContext(BUYER).firestore();
  await assertSucceeds(
    setDoc(doc(buyer, "stores", STORE_ID, "customers", BUYER), {
      cart: [{ productId: "p1", variantKey: "", quantity: 2, price: 100 }],
    }),
  );

  const outsider = env.authenticatedContext(OUTSIDER).firestore();
  await assertFails(
    setDoc(doc(outsider, "stores", STORE_ID, "customers", BUYER), { cart: [] }),
  );
});

test("customers: читают сам покупатель и владелец; посторонний — нет", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "stores", STORE_ID, "customers", BUYER), { cart: [] });
  });

  await assertSucceeds(
    getDoc(doc(env.authenticatedContext(BUYER).firestore(), "stores", STORE_ID, "customers", BUYER)),
  );
  await assertSucceeds(
    getDoc(doc(env.authenticatedContext(OWNER).firestore(), "stores", STORE_ID, "customers", BUYER)),
  );
  await assertFails(
    getDoc(doc(env.authenticatedContext(OUTSIDER).firestore(), "stores", STORE_ID, "customers", BUYER)),
  );
});
