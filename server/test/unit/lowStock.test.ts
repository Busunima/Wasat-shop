import assert from "node:assert/strict";
import { test } from "node:test";
import { buildLowStockBody, crossedLowStock } from "../../src/services/lowStock.ts";
import { storeUpdateSchema } from "../../src/schemas/store.ts";

test("crossedLowStock: срабатывает только на пересечении порога", () => {
  assert.equal(crossedLowStock(5, 2, 3), true); // пересёк сверху вниз
  assert.equal(crossedLowStock(4, 3, 3), true); // лёг ровно на порог
  assert.equal(crossedLowStock(5, 4, 3), false); // всё ещё выше порога
  assert.equal(crossedLowStock(3, 2, 3), false); // уже был на пороге — не спамим
  assert.equal(crossedLowStock(2, 1, 3), false); // уже был ниже
  assert.equal(crossedLowStock(1, 0, 0), true); // порог 0 — распродано
  assert.equal(crossedLowStock(0, 0, 0), false);
});

test("buildLowStockBody: перечисление товаров с остатками", () => {
  const body = buildLowStockBody([
    { productId: "p1", name: "Кеды", remaining: 2 },
    { productId: "p2", name: "Шарф", remaining: 0 },
  ]);
  assert.equal(body, "«Кеды»: осталось 2; «Шарф»: осталось 0");
});

test("storeUpdateSchema: lowStockThreshold — целое 0..10000, null — сброс", () => {
  assert.equal(storeUpdateSchema.parse({ lowStockThreshold: 5 }).lowStockThreshold, 5);
  assert.equal(storeUpdateSchema.parse({ lowStockThreshold: null }).lowStockThreshold, null);
  assert.equal(storeUpdateSchema.parse({}).lowStockThreshold, undefined);
  assert.throws(() => storeUpdateSchema.parse({ lowStockThreshold: -1 }));
  assert.throws(() => storeUpdateSchema.parse({ lowStockThreshold: 2.5 }));
});
