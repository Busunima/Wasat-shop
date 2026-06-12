import type { ApiOrder } from "./orders.js";

/**
 * CSV-экспорт заказов (ТЗ §6 FR-A05). Чистый рендер под unit-тестом; роут отдаёт
 * text/csv. Разделитель — запятая, экранирование по RFC 4180 (кавычки удваиваются,
 * поля с запятой/кавычкой/переводом строки берутся в кавычки). UTF-8 BOM — чтобы
 * Excel корректно открывал кириллицу.
 */

export const CSV_BOM = "\uFEFF";

const HEADER = [
  "orderId",
  "createdAt",
  "status",
  "customerEmail",
  "items",
  "subtotal",
  "discount",
  "deliveryCost",
  "total",
  "currency",
  "promoCode",
  "paymentMethod",
  "paidAt",
  "trackingNo",
  "address",
];

/** Экранирование одного поля по RFC 4180. */
export function csvField(value: string): string {
  return /[",\n\r]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

function isoDate(ms: number | null): string {
  return ms == null ? "" : new Date(ms).toISOString();
}

/** Минорные единицы → «12.34» (как в инвойсе). */
function money(minor: number): string {
  const sign = minor < 0 ? "-" : "";
  const abs = Math.abs(Math.round(minor));
  return `${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, "0")}`;
}

function itemsSummary(order: ApiOrder): string {
  return order.items
    .map((item) => {
      const parts = [item.variant?.size, item.variant?.color, item.variant?.sku].filter(
        (v): v is string => Boolean(v),
      );
      const label = parts.length ? ` (${parts.join("/")})` : "";
      return `${item.name}${label} x${item.qty}`;
    })
    .join("; ");
}

/** Чистый рендер CSV по списку заказов (новые сверху — как отдаёт listOrders). */
export function buildOrdersCsv(orders: ApiOrder[]): string {
  const lines = [HEADER.join(",")];
  for (const order of orders) {
    lines.push(
      [
        order.id,
        isoDate(order.createdAt),
        order.status,
        order.customerEmail,
        itemsSummary(order),
        money(order.subtotal),
        money(order.discount),
        money(order.delivery.cost),
        money(order.total),
        order.currency,
        order.promoCode ?? "",
        order.payment.method,
        isoDate(order.payment.paidAt),
        order.delivery.trackingNo ?? "",
        order.delivery.address ?? "",
      ]
        .map(csvField)
        .join(","),
    );
  }
  return CSV_BOM + lines.join("\r\n") + "\r\n";
}
