import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import {
  collectAllStoreTokens,
  collectStockSubscriberUids,
  collectTokensForProduct,
  notifyNewProduct,
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

test("stockNotifications: явный подписчик получает back_in_stock и подписка снимается", async () => {
  // cust-3: товара НЕТ в вишлисте, но есть явная подписка «уведомить о поступлении»
  const cust3 = db().collection("stores").doc(STORE_ID).collection("customers").doc("cust-3");
  await cust3.set({ stockNotifications: [productId] });
  await registerPushToken(STORE_ID, "cust-3", "tok-cust-3-cccccccc", "android");

  assert.deepEqual(await collectStockSubscriberUids(STORE_ID, productId), ["cust-3"]);

  // back_in_stock: вишлист (cust-1, 2 токена) + подписчик (cust-3, 1 токен)
  const targets = await notifyProductEvent(STORE_ID, productId, "back_in_stock", "Кеды");
  assert.equal(targets, 3);

  // одноразовость: подписка снята, вишлист cust-1 не тронут
  assert.deepEqual((await cust3.get()).data()?.["stockNotifications"], []);
  const cust1 = await db()
    .collection("stores").doc(STORE_ID).collection("customers").doc("cust-1").get();
  assert.deepEqual(cust1.data()?.["wishlist"], [productId]);

  // price_drop подписчиков stockNotifications не трогает (только вишлист)
  await cust3.set({ stockNotifications: [productId] });
  const priceTargets = await notifyProductEvent(STORE_ID, productId, "price_drop", "Кеды");
  assert.equal(priceTargets, 2);
  assert.deepEqual((await cust3.get()).data()?.["stockNotifications"], [productId]);
  await cust3.set({ stockNotifications: [] });
});

test("notifyNewProduct: адресаты — все токены магазина; 0 для пустого магазина", async () => {
  const all = await collectAllStoreTokens(STORE_ID);
  assert.ok(all.length >= 3); // cust-1 (2) + cust-2 (1) + cust-3 (1)
  const targets = await notifyNewProduct(STORE_ID, "any-product-id", "Новинка");
  assert.equal(targets, all.length); // broadcast по всем покупателям, не по вишлисту
  const none = await notifyNewProduct("empty-store-xyz", "p1", "P");
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
