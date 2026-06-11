import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import { createProduct } from "../../src/services/products.ts";
import { createReview, deleteReview, listReviews, reviewIdFor } from "../../src/services/reviews.ts";
import { getProduct } from "../../src/services/products.ts";

/** Интеграционные тесты FR-B08: право на отзыв по заказу + агрегаты товара. */

const STORE_ID = "reviews-store";
const BUYER = "rev-buyer";
let productId = "";

async function seedOrder(id: string, status: string, items: Array<{ productId: string }>) {
  await db().collection("stores").doc(STORE_ID).collection("orders").doc(id).set({
    id,
    customerUid: BUYER,
    status,
    items,
  });
}

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "reviews",
    name: "Reviews",
    ownerUid: "owner-rev",
    currency: "USD",
    plan: "enterprise",
    isPublic: true,
  });
  const p = await createProduct(STORE_ID, {
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
  productId = p.id;
  await seedOrder("ord-delivered", "DELIVERED", [{ productId }]);
  await seedOrder("ord-new", "NEW", [{ productId }]);
  await seedOrder("ord-other", "DELIVERED", [{ productId: "other-product" }]);
});

test("createReview: доставленный заказ → отзыв + агрегаты товара", async () => {
  const review = await createReview(STORE_ID, productId, BUYER, {
    rating: 4,
    text: "Хорошие",
    photos: [],
    orderId: "ord-delivered",
  });
  assert.equal(review.rating, 4);
  assert.equal(review.id, reviewIdFor(BUYER, productId));

  const product = await getProduct(STORE_ID, productId, false);
  assert.equal(product.reviewCount, 1);
  assert.equal(product.rating, 4);
});

test("createReview: повтор тем же пользователем — обновление, счётчик не растёт", async () => {
  await createReview(STORE_ID, productId, BUYER, {
    rating: 2,
    photos: [],
    orderId: "ord-delivered",
  });
  const product = await getProduct(STORE_ID, productId, false);
  assert.equal(product.reviewCount, 1); // не удвоился
  assert.equal(product.rating, 2); // среднее = 2
});

test("createReview: заказ не доставлен / не содержит товар / чужой → отказ", async () => {
  await assert.rejects(
    () => createReview(STORE_ID, productId, BUYER, { rating: 5, photos: [], orderId: "ord-new" }),
    (e: { code?: string }) => e.code === "FORBIDDEN",
  );
  await assert.rejects(
    () => createReview(STORE_ID, productId, BUYER, { rating: 5, photos: [], orderId: "ord-other" }),
    (e: { code?: string }) => e.code === "VALIDATION_ERROR",
  );
  await assert.rejects(
    () =>
      createReview(STORE_ID, productId, "stranger", {
        rating: 5,
        photos: [],
        orderId: "ord-delivered",
      }),
    (e: { code?: string }) => e.code === "FORBIDDEN",
  );
});

test("listReviews: публичный список товара", async () => {
  const list = await listReviews(STORE_ID, productId, 20);
  assert.equal(list.length, 1);
  assert.equal(list[0]!.customerUid, BUYER);
});

test("deleteReview: модерация пересчитывает агрегаты до нуля", async () => {
  await deleteReview(STORE_ID, reviewIdFor(BUYER, productId));
  const product = await getProduct(STORE_ID, productId, false);
  assert.equal(product.reviewCount, 0);
  assert.equal(product.rating, 0);
  await assert.rejects(
    () => deleteReview(STORE_ID, reviewIdFor(BUYER, productId)),
    (e: { code?: string }) => e.code === "NOT_FOUND",
  );
});
