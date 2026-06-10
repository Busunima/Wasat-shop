import assert from "node:assert/strict";
import { test } from "node:test";
import { parseInventoryCsv, stockAdjustSchema } from "../../src/schemas/inventory.ts";

test("parseInventoryCsv: заголовок, разделители ',' и ';', пустые строки", () => {
  const csv = ["sku,stock", "SKU-1,5", "SKU-2;0", "", "SKU-3, 12 "].join("\n");
  const parsed = parseInventoryCsv(csv);
  assert.equal(parsed.errors.length, 0);
  assert.deepEqual(
    parsed.rows.map((r) => [r.sku, r.stock]),
    [
      ["SKU-1", 5],
      ["SKU-2", 0],
      ["SKU-3", 12],
    ],
  );
});

test("parseInventoryCsv: невалидные строки попадают в отчёт, валидные применяются", () => {
  const csv = ["SKU-1,5", "битая строка", "SKU-2,-3", "SKU-3,1.5", "SKU-4,7"].join("\n");
  const parsed = parseInventoryCsv(csv);
  assert.deepEqual(parsed.rows.map((r) => r.sku), ["SKU-1", "SKU-4"]);
  assert.equal(parsed.errors.length, 3);
  assert.deepEqual(parsed.errors.map((e) => e.line), [2, 3, 4]);
});

test("stockAdjustSchema: дельта int, селектор варианта требует sku или size/color", () => {
  const ok = stockAdjustSchema.parse({ delta: -2, variant: { sku: "S1" } });
  assert.equal(ok.reason, "manual");

  assert.throws(() => stockAdjustSchema.parse({ delta: 1.5 }));
  assert.throws(() => stockAdjustSchema.parse({ delta: 1, variant: {} }));
});
