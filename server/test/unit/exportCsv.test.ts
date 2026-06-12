import assert from "node:assert/strict";
import { test } from "node:test";
import { CSV_BOM, buildOrdersCsv, csvField } from "../../src/services/exportCsv.ts";
import type { ApiOrder } from "../../src/services/orders.ts";

const order: ApiOrder = {
  id: "abc123",
  customerUid: "buyer-1",
  customerEmail: "buyer@example.com",
  items: [
    { productId: "p1", name: "Куртка, зимняя", qty: 2, price: 4999, variant: { size: "M" } },
    { productId: "p2", name: "Шарф", qty: 1, price: 1500, variant: null },
  ],
  subtotal: 11498,
  tax: 0,
  discount: 1000,
  total: 10998,
  currency: "USD",
  promoCode: "SALE10",
  status: "SHIPPED",
  delivery: { method: "courier", address: "ул. Мира, д. 5", cost: 500, trackingNo: "TRK-1" },
  payment: { method: "deferred", paidAt: null },
  createdAt: Date.UTC(2026, 0, 15, 12, 30),
};

test("csvField: экранирование по RFC 4180", () => {
  assert.equal(csvField("plain"), "plain");
  assert.equal(csvField("a,b"), '"a,b"');
  assert.equal(csvField('say "hi"'), '"say ""hi"""');
  assert.equal(csvField("line1\nline2"), '"line1\nline2"');
});

test("buildOrdersCsv: BOM, заголовок, строки и суммы в мажорных единицах", () => {
  const csv = buildOrdersCsv([order]);
  assert.ok(csv.startsWith(CSV_BOM + "orderId,createdAt,status"));
  const lines = csv.slice(CSV_BOM.length).trimEnd().split("\r\n");
  assert.equal(lines.length, 2); // заголовок + 1 заказ
  const row = lines[1]!;
  assert.ok(row.startsWith("abc123,2026-01-15T12:30:00.000Z,SHIPPED,buyer@example.com"));
  assert.ok(row.includes('"Куртка, зимняя (M) x2; Шарф x1"')); // запятая в имени → кавычки
  assert.ok(row.includes("114.98")); // subtotal
  assert.ok(row.includes("109.98")); // total
  assert.ok(row.includes("SALE10"));
  assert.ok(row.includes("TRK-1"));
});

test("buildOrdersCsv: пустой список — только заголовок", () => {
  const csv = buildOrdersCsv([]);
  const lines = csv.slice(CSV_BOM.length).trimEnd().split("\r\n");
  assert.equal(lines.length, 1);
});
