import assert from "node:assert/strict";
import { test } from "node:test";
import { rankRelated } from "../../src/services/recommendations.ts";
import type { ApiProduct } from "../../src/services/products.ts";

function product(
  id: string,
  opts: { tags?: string[]; rating?: number; stock?: number } = {},
): ApiProduct & { rating: number } {
  return {
    id,
    name: id,
    description: "",
    price: 1000,
    originalPrice: null,
    images: [],
    category: "shoes",
    tags: opts.tags ?? [],
    variants: [],
    totalStock: opts.stock ?? 5,
    status: "active",
    sku: null,
    barcode: null,
    rating: opts.rating ?? 0,
  };
}

const current = { id: "p0", tags: ["red", "summer"] };

test("rankRelated: исключает сам товар", () => {
  const out = rankRelated(current, [product("p0"), product("p1")], 5);
  assert.deepEqual(out.map((p) => p.id), ["p1"]);
});

test("rankRelated: больше общих тегов — выше", () => {
  const out = rankRelated(
    current,
    [
      product("a", { tags: ["red"] }), // 1 общий
      product("b", { tags: ["red", "summer"] }), // 2 общих
      product("c", { tags: ["blue"] }), // 0 общих
    ],
    5,
  );
  assert.deepEqual(out.map((p) => p.id), ["b", "a", "c"]);
});

test("rankRelated: при равных тегах — выше рейтинг, затем наличие", () => {
  const out = rankRelated(
    current,
    [
      product("low", { tags: ["red"], rating: 2 }),
      product("high", { tags: ["red"], rating: 5 }),
      product("oos", { tags: ["red"], rating: 5, stock: 0 }),
    ],
    5,
  );
  // high и oos: rating равны (5), но high в наличии → выше oos; low ниже по рейтингу
  assert.deepEqual(out.map((p) => p.id), ["high", "oos", "low"]);
});

test("rankRelated: соблюдает limit", () => {
  const out = rankRelated(current, [product("a"), product("b"), product("c")], 2);
  assert.equal(out.length, 2);
});
