import assert from "node:assert/strict";
import { test } from "node:test";
import { recomputeRating, reviewCreateSchema } from "../../src/schemas/review.ts";

test("reviewCreateSchema: rating 1..5, photos ≤6, orderId обязателен", () => {
  const ok = reviewCreateSchema.parse({ rating: 5, orderId: "o1" });
  assert.equal(ok.rating, 5);
  assert.deepEqual(ok.photos, []);
  assert.throws(() => reviewCreateSchema.parse({ rating: 0, orderId: "o1" }));
  assert.throws(() => reviewCreateSchema.parse({ rating: 6, orderId: "o1" }));
  assert.throws(() => reviewCreateSchema.parse({ rating: 3 })); // нет orderId
  assert.throws(() =>
    reviewCreateSchema.parse({
      rating: 3,
      orderId: "o1",
      photos: Array(7).fill("https://x.test/a.jpg"),
    }),
  );
});

test("recomputeRating: добавление, обновление, удаление, округление", () => {
  // первый отзыв: 5
  let agg = recomputeRating(0, 0, { addSum: 5, addCount: 1 });
  assert.deepEqual(agg, { ratingSum: 5, reviewCount: 1, rating: 5 });

  // второй: 4 → среднее 4.5
  agg = recomputeRating(5, 1, { addSum: 4, addCount: 1 });
  assert.deepEqual(agg, { ratingSum: 9, reviewCount: 2, rating: 4.5 });

  // округление до 0.1: 5+4+5 = 14 / 3 = 4.666… → 4.7
  agg = recomputeRating(9, 2, { addSum: 5, addCount: 1 });
  assert.equal(agg.rating, 4.7);

  // обновление отзыва (5→2): счётчик не растёт, сумма -3
  agg = recomputeRating(14, 3, { addSum: -3, addCount: 0 });
  assert.equal(agg.reviewCount, 3);
  assert.equal(agg.ratingSum, 11);

  // удаление последнего → 0 отзывов, рейтинг 0
  agg = recomputeRating(5, 1, { addSum: -5, addCount: -1 });
  assert.deepEqual(agg, { ratingSum: 0, reviewCount: 0, rating: 0 });
});

test("recomputeRating: защита от отрицательных значений", () => {
  const agg = recomputeRating(0, 0, { addSum: -5, addCount: -1 });
  assert.equal(agg.ratingSum, 0);
  assert.equal(agg.reviewCount, 0);
  assert.equal(agg.rating, 0);
});
