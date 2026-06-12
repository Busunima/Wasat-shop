import assert from "node:assert/strict";
import { test } from "node:test";
import { buildInvoiceHtml, escapeHtml, formatMoney } from "../../src/services/invoice.ts";
import type { ApiOrder } from "../../src/services/orders.ts";

const baseOrder: ApiOrder = {
  id: "abcdef1234567890abcdef1234567890",
  customerUid: "buyer-1",
  customerEmail: "buyer@example.com",
  items: [
    { productId: "p1", name: "Куртка", qty: 2, price: 4999, variant: { size: "M", color: "синий" } },
    { productId: "p2", name: "Шарф", qty: 1, price: 1500, variant: null },
  ],
  subtotal: 11498,
  tax: 0,
  discount: 1000,
  total: 10998,
  currency: "USD",
  promoCode: "SALE10",
  status: "CONFIRMED",
  delivery: { method: "courier", address: "ул. Пушкина, 1", cost: 500, trackingNo: null },
  payment: { method: "deferred", paidAt: null },
  createdAt: Date.UTC(2026, 0, 15),
};

test("formatMoney: минорные единицы → строка с двумя знаками", () => {
  assert.equal(formatMoney(0), "0.00");
  assert.equal(formatMoney(5), "0.05");
  assert.equal(formatMoney(1500), "15.00");
  assert.equal(formatMoney(10998), "109.98");
  assert.equal(formatMoney(-1000), "-10.00");
});

test("escapeHtml: спецсимволы экранируются (XSS-безопасно)", () => {
  assert.equal(escapeHtml(`<script>"&'`), "&lt;script&gt;&quot;&amp;&#39;");
});

test("buildInvoiceHtml: содержит номер, позиции, итог и реквизиты", () => {
  const html = buildInvoiceHtml(baseOrder, {
    name: "Магазин «Стиль»",
    contactEmail: "shop@example.com",
    contactPhone: "+1 555",
  });
  assert.match(html, /Инвойс №ABCDEF12/); // первые 8 символов id, upper
  assert.ok(html.includes("Куртка (M, синий)")); // вариант в названии
  assert.ok(html.includes("Шарф"));
  assert.ok(html.includes("buyer@example.com"));
  assert.ok(html.includes("ул. Пушкина, 1"));
  assert.ok(html.includes("SALE10")); // промокод в строке скидки
  assert.ok(html.includes("109.98")); // итог в мажорных единицах
  assert.ok(html.includes("114.98")); // подытог
  assert.ok(html.includes("к оплате")); // не оплачено
  assert.ok(html.includes("Магазин «Стиль»")); // имя магазина (кириллица + кавычки)
});

test("buildInvoiceHtml: оплаченный заказ помечается, имя товара экранируется", () => {
  const order: ApiOrder = {
    ...baseOrder,
    items: [{ productId: "p", name: "<b>Хак</b>", qty: 1, price: 100, variant: null }],
    discount: 0,
    promoCode: null,
    payment: { method: "card", paidAt: Date.UTC(2026, 0, 16) },
  };
  const html = buildInvoiceHtml(order, { name: "Shop", contactEmail: null, contactPhone: null });
  assert.ok(html.includes("оплачено"));
  assert.ok(html.includes("&lt;b&gt;Хак&lt;/b&gt;")); // имя экранировано
  assert.ok(!html.includes("Скидка")); // нет скидки — строки нет
});
