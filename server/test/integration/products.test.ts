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
import { productListQuerySchema, productUpdateSchema } from "../../src/schemas/product.ts";

/** Дефолтные параметры листинга (как parse пустого query в роуте). */
function listQuery(overrides: Record<string, unknown> = {}) {
  return productListQuerySchema.parse(overrides);
}

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

  const publicList = (await listProducts(STORE_ID, false, listQuery())).items;
  assert.ok(publicList.every((p) => p.status === "active"));
  assert.ok(!publicList.some((p) => p.name === "Черновик"));

  const ownerList = (await listProducts(STORE_ID, true, listQuery())).items;
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

test("листинг FR-B02: пагинация, фильтры, сортировка", async (t) => {
  const PAGED_STORE = "paged-store";
  await db().collection("stores").doc(PAGED_STORE).set({
    id: PAGED_STORE,
    slug: "paged",
    name: "Paged",
    ownerUid: "owner-9",
    currency: "USD",
    isPublic: true,
  });

  // 7 товаров: цены 100..700, категории A/B, один draft, один без остатка
  const seed = [
    { name: "Alpha", price: 100, category: "A", stock: 1, status: "active" },
    { name: "Bravo", price: 200, category: "B", stock: 0, status: "active" },
    { name: "Charlie", price: 300, category: "A", stock: 5, status: "active" },
    { name: "Delta", price: 400, category: "B", stock: 2, status: "active" },
    { name: "Echo", price: 500, category: "A", stock: 0, status: "active" },
    { name: "Foxtrot", price: 600, category: "B", stock: 3, status: "draft" },
    { name: "Golf", price: 700, category: "A", stock: 9, status: "active" },
  ];
  for (const s of seed) {
    await createProduct(PAGED_STORE, {
      name: s.name,
      price: s.price,
      images: [],
      tags: [],
      category: s.category,
      variants: s.stock > 0 ? [{ stock: s.stock }] : [],
      status: s.status as "active" | "draft",
    });
  }

  await t.test("курсорная пагинация без дублей и потерь (sort=price_asc)", async () => {
    const page1 = await listProducts(PAGED_STORE, false, listQuery({ sort: "price_asc", limit: "3" }));
    assert.equal(page1.items.length, 3);
    assert.ok(page1.nextCursor);
    const page2 = await listProducts(
      PAGED_STORE,
      false,
      listQuery({ sort: "price_asc", limit: "3", cursor: page1.nextCursor }),
    );
    const all = [...page1.items, ...page2.items].map((p) => p.name);
    // 6 активных по возрастанию цены, draft Foxtrot исключён
    assert.deepEqual(all, ["Alpha", "Bravo", "Charlie", "Delta", "Echo", "Golf"]);
  });

  await t.test("фильтр категории + сортировка по убыванию цены", async () => {
    const page = await listProducts(
      PAGED_STORE,
      false,
      listQuery({ category: "A", sort: "price_desc" }),
    );
    assert.deepEqual(page.items.map((p) => p.name), ["Golf", "Echo", "Charlie", "Alpha"]);
  });

  await t.test("inStock и диапазон цены — постфильтры", async () => {
    const inStock = await listProducts(
      PAGED_STORE,
      false,
      listQuery({ inStock: "true", sort: "price_asc" }),
    );
    assert.deepEqual(inStock.items.map((p) => p.name), ["Alpha", "Charlie", "Delta", "Golf"]);

    const ranged = await listProducts(
      PAGED_STORE,
      false,
      listQuery({ minPrice: "200", maxPrice: "500", sort: "price_asc" }),
    );
    assert.deepEqual(ranged.items.map((p) => p.name), ["Bravo", "Charlie", "Delta", "Echo"]);
  });

  await t.test("поиск q — подстрока без регистра", async () => {
    const found = await listProducts(PAGED_STORE, false, listQuery({ q: "cha" }));
    assert.deepEqual(found.items.map((p) => p.name), ["Charlie"]);
  });

  await t.test("битый курсор игнорируется (первая страница)", async () => {
    const page = await listProducts(
      PAGED_STORE,
      false,
      listQuery({ sort: "price_asc", limit: "2", cursor: "не-курсор" }),
    );
    assert.deepEqual(page.items.map((p) => p.name), ["Alpha", "Bravo"]);
  });
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
