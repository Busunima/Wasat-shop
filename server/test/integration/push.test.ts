import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import {
  collectTokensForProduct,
  notifyProductEvent,
  registerPushToken,
} from "../../src/services/push.ts";
import { createProduct, updateProduct } from "../../src/services/products.ts";
import { adjustStock } from "../../src/services/inventory.ts";

/**
 * Интеграционные тесты FR-B10 против эмулятора: регистрация токенов и сбор
 * адресатов по вишлисту. Реальная отправка FCM в тестах не выполняется
 * (эмулятора FCM нет) — notifyProductEvent логирует сбой и возвращает число целей.
 */

const STORE_ID = "push-store";
let productId = "";

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "push",
    name: "Push",
    ownerUid: "owner-push",
    currency: "USD",
    plan: "enterprise",
    isPublic: true,
  });
  const product = await createProduct(STORE_ID, {
    name: "Кеды",
    description: "",
    price: 5000,
    originalPrice: undefined,
    images: [],
    category: "shoes",
    tags: [],
    variants: [],
    status: "active",
    sku: undefined,
    barcode: undefined,
  });
  productId = product.id;

  // Покупатель с товаром в вишлисте + зарегистрированный токен
  await db()
    .collection("stores")
    .doc(STORE_ID)
    .collection("customers")
    .doc("cust-1")
    .set({ wishlist: [productId] });
  await registerPushToken(STORE_ID, "cust-1", "tok-cust-1-aaaaaaaa", "android");
  // Покупатель без этого товара в вишлисте
  await db()
    .collection("stores")
    .doc(STORE_ID)
    .collection("customers")
    .doc("cust-2")
    .set({ wishlist: ["other-product"] });
  await registerPushToken(STORE_ID, "cust-2", "tok-cust-2-bbbbbbbb", "android");
});

test("registerPushToken: идемпотентность (arrayUnion)", async () => {
  await registerPushToken(STORE_ID, "cust-1", "tok-cust-1-aaaaaaaa", "android");
  await registerPushToken(STORE_ID, "cust-1", "tok-cust-1-second00", "android");
  const snap = await db()
    .collection("stores")
    .doc(STORE_ID)
    .collection("fcmTokens")
    .doc("cust-1")
    .get();
  const tokens = (snap.data()?.["tokens"] as string[]) ?? [];
  assert.equal(tokens.length, 2); // дубликат не добавился, второй токен — да
});

test("collectTokensForProduct: только адресаты с товаром в вишлисте", async () => {
  const tokens = await collectTokensForProduct(STORE_ID, productId);
  assert.ok(tokens.includes("tok-cust-1-aaaaaaaa"));
  assert.ok(tokens.includes("tok-cust-1-second00"));
  assert.ok(!tokens.includes("tok-cust-2-bbbbbbbb"));
});

test("notifyProductEvent: возвращает число целей, не бросает без FCM", async () => {
  const targets = await notifyProductEvent(STORE_ID, productId, "back_in_stock", "Кеды");
  assert.equal(targets, 2);
  const none = await notifyProductEvent(STORE_ID, "ghost-product", "price_drop", "Ghost");
  assert.equal(none, 0);
});

test("триггеры: снижение цены и переход 0→в наличии не ломают операции", async () => {
  // price_drop: 5000 → 4000 (fire-and-forget внутри updateProduct)
  const updated = await updateProduct(STORE_ID, productId, { price: 4000 });
  assert.equal(updated.price, 4000);

  // back_in_stock: товар без вариантов 0 → 3
  const stocked = await adjustStock(STORE_ID, productId, "owner-push", {
    delta: 3,
    reason: "restock",
  });
  assert.equal(stocked.totalStock, 3);
});
