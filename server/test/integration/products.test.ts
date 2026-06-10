import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import {
  createProduct,
  deleteProduct,
  getProduct,
  listProducts,
  updateProduct,
} from "../../src/services/products.ts";
import { ApiError } from "../../src/middleware/errorHandler.ts";
import { getStoreInfo } from "../../src/services/stores.ts";
import { productUpdateSchema } from "../../src/schemas/product.ts";

/**
 * Интеграционные тесты CRUD товаров против эмулятора Firestore.
 * Запуск: npm run test:integration (firebase emulators:exec ...).
 */

const STORE_ID = "products-test-store";

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "products-test",
    name: "Products Test",
    ownerUid: "owner-1",
    currency: "USD",
    isPublic: true,
  });
});

test("getStoreInfo: публичный магазин виден; приватный — только владельцу", async () => {
  const info = await getStoreInfo(STORE_ID, false);
  assert.equal(info.slug, "products-test");
  assert.equal(info.currency, "USD");

  const privateId = "private-store";
  await db().collection("stores").doc(privateId).set({
    id: privateId,
    slug: "private",
    name: "Private",
    ownerUid: "owner-2",
    currency: "EUR",
    isPublic: false,
  });
  await assert.rejects(
    () => getStoreInfo(privateId, false),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );
  const asOwner = await getStoreInfo(privateId, true);
  assert.equal(asOwner.currency, "EUR");
});

test("createProduct: создаёт товар, totalStock — сумма вариантов", async () => {
  const product = await createProduct(STORE_ID, {
    name: "Кеды",
    price: 9_990,
    images: [],
    tags: [],
    variants: [
      { size: "41", stock: 2 },
      { size: "42", stock: 3 },
    ],
    status: "active",
  });

  assert.ok(product.id);
  assert.equal(product.totalStock, 5);
  assert.equal(product.status, "active");

  const snap = await db()
    .collection("stores")
    .doc(STORE_ID)
    .collection("products")
    .doc(product.id)
    .get();
  assert.equal(snap.data()?.["totalStock"], 5);
  assert.equal(snap.data()?.["rating"], 0);
});

test("listProducts: посетителю — только active; владельцу — все", async () => {
  await createProduct(STORE_ID, {
    name: "Черновик",
    price: 100,
    images: [],
    tags: [],
    variants: [],
    status: "draft",
  });

  const publicList = await listProducts(STORE_ID, false);
  assert.ok(publicList.every((p) => p.status === "active"));
  assert.ok(!publicList.some((p) => p.name === "Черновик"));

  const ownerList = await listProducts(STORE_ID, true);
  assert.ok(ownerList.some((p) => p.name === "Черновик"));
});

test("getProduct: draft скрыт от посетителя, виден владельцу", async () => {
  const draft = await createProduct(STORE_ID, {
    name: "Скрытый",
    price: 1,
    images: [],
    tags: [],
    variants: [],
    status: "draft",
  });

  await assert.rejects(
    () => getProduct(STORE_ID, draft.id, false),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );
  const asOwner = await getProduct(STORE_ID, draft.id, true);
  assert.equal(asOwner.name, "Скрытый");
});

test("updateProduct: пересчитывает totalStock при изменении вариантов", async () => {
  const product = await createProduct(STORE_ID, {
    name: "Футболка",
    price: 1_990,
    images: [],
    tags: [],
    variants: [{ size: "M", stock: 1 }],
    status: "active",
  });

  const updated = await updateProduct(STORE_ID, product.id, {
    variants: [
      { size: "M", stock: 10 },
      { size: "L", stock: 7 },
    ],
  });
  assert.equal(updated.totalStock, 17);

  // Обновление без вариантов не трогает totalStock
  const repriced = await updateProduct(STORE_ID, product.id, { price: 2_490 });
  assert.equal(repriced.totalStock, 17);
  assert.equal(repriced.price, 2_490);
});

test("sku/barcode/category/tags: round-trip и очистка через PATCH", async () => {
  const product = await createProduct(STORE_ID, {
    name: "С артикулом",
    price: 100,
    images: [],
    tags: ["обувь", "лето"],
    category: "Кеды",
    sku: "SKU-9",
    barcode: "4600000000017",
    variants: [],
    status: "active",
  });
  assert.equal(product.sku, "SKU-9");
  assert.equal(product.barcode, "4600000000017");
  assert.equal(product.category, "Кеды");
  assert.deepEqual(product.tags, ["обувь", "лето"]);

  // "" в PATCH очищает поле; отсутствующие поля не трогаются.
  // Вход прогоняется через схему, как в роуте (transform "" -> null живёт в zod).
  const updated = await updateProduct(
    STORE_ID,
    product.id,
    productUpdateSchema.parse({ barcode: "" }),
  );
  assert.equal(updated.barcode, null);
  assert.equal(updated.sku, "SKU-9");
  assert.deepEqual(updated.tags, ["обувь", "лето"]);
});

test("deleteProduct: удаляет; повторное удаление — NOT_FOUND", async () => {
  const product = await createProduct(STORE_ID, {
    name: "Временный",
    price: 1,
    images: [],
    tags: [],
    variants: [],
    status: "active",
  });

  await deleteProduct(STORE_ID, product.id);
  await assert.rejects(
    () => deleteProduct(STORE_ID, product.id),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );
});

test("createProduct: несуществующий магазин — NOT_FOUND", async () => {
  await assert.rejects(
    () =>
      createProduct("no-such-store", {
        name: "X",
        price: 1,
        images: [],
        tags: [],
        variants: [],
        status: "active",
      }),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );
});
