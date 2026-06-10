import assert from "node:assert/strict";
import { test } from "node:test";
import {
  computeTotalStock,
  productCreateSchema,
  productUpdateSchema,
} from "../../src/schemas/product.ts";

test("productCreateSchema: валидный товар с дефолтами", () => {
  const parsed = productCreateSchema.parse({ name: "Кроссовки", price: 12_990 });
  assert.equal(parsed.status, "draft");
  assert.deepEqual(parsed.images, []);
  assert.deepEqual(parsed.tags, []);
  assert.deepEqual(parsed.variants, []);
});

test("productCreateSchema: отклоняет дробные и отрицательные цены", () => {
  assert.throws(() => productCreateSchema.parse({ name: "X", price: 99.5 }));
  assert.throws(() => productCreateSchema.parse({ name: "X", price: -1 }));
});

test("productCreateSchema: отклоняет пустое имя и плохой статус", () => {
  assert.throws(() => productCreateSchema.parse({ name: "", price: 1 }));
  assert.throws(() => productCreateSchema.parse({ name: "X", price: 1, status: "hidden" }));
});

test("productCreateSchema: images должны быть URL", () => {
  assert.throws(() =>
    productCreateSchema.parse({ name: "X", price: 1, images: ["не-урл"] }),
  );
  const ok = productCreateSchema.parse({
    name: "X",
    price: 1,
    images: ["https://storage.example.com/p.png"],
  });
  assert.equal(ok.images.length, 1);
});

test("productCreateSchema: stock варианта неотрицательный int", () => {
  assert.throws(() =>
    productCreateSchema.parse({ name: "X", price: 1, variants: [{ stock: -1 }] }),
  );
});

test("productUpdateSchema: частичное обновление допустимо", () => {
  const parsed = productUpdateSchema.parse({ price: 500 });
  assert.equal(parsed.price, 500);
  assert.equal(parsed.name, undefined);
});

test("sku/barcode/category: trim, '' -> null (очистка), отсутствие -> undefined", () => {
  const parsed = productCreateSchema.parse({
    name: "X",
    price: 1,
    sku: "  SKU-1  ",
    barcode: "",
  });
  assert.equal(parsed.sku, "SKU-1");
  assert.equal(parsed.barcode, null);
  assert.equal(parsed.category, undefined);
  assert.throws(() => productCreateSchema.parse({ name: "X", price: 1, sku: "x".repeat(65) }));
});

test("originalPrice: принимает null (сброс старой цены)", () => {
  const parsed = productUpdateSchema.parse({ originalPrice: null });
  assert.equal(parsed.originalPrice, null);
  assert.throws(() => productUpdateSchema.parse({ originalPrice: 9.99 }));
});

test("computeTotalStock: сумма по вариантам; пусто — 0", () => {
  assert.equal(computeTotalStock([]), 0);
  assert.equal(
    computeTotalStock([
      { stock: 3, size: "M" },
      { stock: 5, size: "L" },
      { stock: 0, size: "XL" },
    ]),
    8,
  );
});
