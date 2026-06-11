import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import { createProduct } from "../../src/services/products.ts";
import { recordEvent } from "../../src/services/analytics.ts";
import { getPopular, getRelated } from "../../src/services/recommendations.ts";
import type { ProductCreate } from "../../src/schemas/product.ts";

/** Интеграционные тесты FR-B12: похожие и популярное против эмулятора. */

const STORE_ID = "rec-store";
const ids: Record<string, string> = {};

function product(name: string, category: string, tags: string[] = []): ProductCreate {
  return {
    name,
    description: "",
    price: 1000,
    originalPrice: undefined,
    images: [],
    category,
    tags,
    variants: [{ stock: 5 }],
    status: "active",
    sku: undefined,
    barcode: undefined,
  } as ProductCreate;
}

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "rec",
    name: "Rec",
    ownerUid: "owner-rec",
    currency: "USD",
    plan: "enterprise",
    isPublic: true,
  });
  ids.shoes1 = (await createProduct(STORE_ID, product("Shoes 1", "shoes", ["red"]))).id;
  ids.shoes2 = (await createProduct(STORE_ID, product("Shoes 2", "shoes", ["red", "summer"]))).id;
  ids.shoes3 = (await createProduct(STORE_ID, product("Shoes 3", "shoes", []))).id;
  ids.bag = (await createProduct(STORE_ID, product("Bag", "bags", []))).id;
});

test("getRelated: та же категория, исключая сам товар, без чужой категории", async () => {
  const related = await getRelated(STORE_ID, ids.shoes1!, 2);
  const relIds = related.map((p) => p.id);
  assert.equal(relIds.length, 2);
  assert.ok(!relIds.includes(ids.shoes1!), "не содержит сам товар");
  assert.ok(!relIds.includes(ids.bag!), "не содержит чужую категорию");
  // shoes2 делит тег red+summer (1 общий с red) — выше shoes3 (0 общих)
  assert.equal(relIds[0], ids.shoes2);
});

test("getPopular: порядок отражает просмотры (FR-A05)", async () => {
  await recordEvent(STORE_ID, { type: "product_view", productId: ids.shoes3! });
  await recordEvent(STORE_ID, { type: "product_view", productId: ids.shoes3! });
  await recordEvent(STORE_ID, { type: "product_view", productId: ids.shoes3! });
  await recordEvent(STORE_ID, { type: "product_view", productId: ids.bag! });

  const popular = await getPopular(STORE_ID, 10);
  const popIds = popular.map((p) => p.id);
  // shoes3 (3 просмотра) идёт раньше bag (1 просмотр)
  assert.ok(popIds.indexOf(ids.shoes3!) >= 0);
  assert.ok(popIds.indexOf(ids.shoes3!) < popIds.indexOf(ids.bag!));
});

test("getPopular по категории: только указанная категория", async () => {
  const popular = await getPopular(STORE_ID, 10, "bags");
  assert.ok(popular.every((p) => p.category === "bags"));
});

test("getPopular: откат на рейтинг без аналитики (другой магазин)", async () => {
  const EMPTY = "rec-empty";
  await db().collection("stores").doc(EMPTY).set({
    id: EMPTY,
    slug: "rec-empty",
    name: "Empty",
    ownerUid: "o",
    currency: "USD",
    plan: "enterprise",
    isPublic: true,
  });
  await createProduct(EMPTY, product("Solo", "misc", []));
  const popular = await getPopular(EMPTY, 5);
  assert.equal(popular.length, 1);
  assert.equal(popular[0]!.name, "Solo");
});
