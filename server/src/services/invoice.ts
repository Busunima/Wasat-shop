import { db } from "../lib/firebase.js";
import type { ApiOrder, OrderItem } from "./orders.js";

/**
 * Инвойс заказа (ТЗ §6 FR-A04). Сервер рендерит самодостаточный HTML-документ
 * (инлайн-стили, печатно-дружелюбный) — клиент открывает его и сохраняет в PDF
 * через системную печать Android. HTML выбран намеренно: поддерживает кириллицу
 * без встраивания шрифтов и не тянет внешних зависимостей/ключей.
 */

export interface InvoiceStore {
  name: string;
  contactEmail: string | null;
  contactPhone: string | null;
}

/** Минорные единицы → строка «12.34» (десятичная часть всегда два знака). */
export function formatMoney(minor: number): string {
  const sign = minor < 0 ? "-" : "";
  const abs = Math.abs(Math.round(minor));
  return `${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, "0")}`;
}

/** Экранирование для безопасной вставки пользовательских строк в HTML. */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function variantLabel(variant: OrderItem["variant"]): string {
  if (!variant) return "";
  const parts = [variant.size, variant.color, variant.sku].filter(
    (v): v is string => Boolean(v),
  );
  return parts.length ? ` (${parts.join(", ")})` : "";
}

function invoiceNo(orderId: string): string {
  return orderId.slice(0, 8).toUpperCase();
}

function formatDate(ms: number | null): string {
  if (ms == null) return "—";
  return new Date(ms).toISOString().slice(0, 10);
}

/** Чистый рендер HTML-инвойса (под unit-тестом). */
export function buildInvoiceHtml(order: ApiOrder, store: InvoiceStore): string {
  const cur = escapeHtml(order.currency);
  const rows = order.items
    .map((item) => {
      const name = escapeHtml(item.name) + escapeHtml(variantLabel(item.variant));
      const line = item.price * item.qty;
      return `<tr>
        <td>${name}</td>
        <td class="num">${item.qty}</td>
        <td class="num">${formatMoney(item.price)}</td>
        <td class="num">${formatMoney(line)}</td>
      </tr>`;
    })
    .join("");

  const contact = [store.contactEmail, store.contactPhone]
    .filter((v): v is string => Boolean(v))
    .map((v) => escapeHtml(v))
    .join(" · ");

  const discountRow =
    order.discount > 0
      ? `<tr><td>Скидка${order.promoCode ? ` (${escapeHtml(order.promoCode)})` : ""}</td>
         <td class="num">−${formatMoney(order.discount)} ${cur}</td></tr>`
      : "";
  const deliveryRow =
    order.delivery.cost > 0
      ? `<tr><td>Доставка</td><td class="num">${formatMoney(order.delivery.cost)} ${cur}</td></tr>`
      : "";
  const taxRow =
    order.tax > 0
      ? `<tr><td>Налог</td><td class="num">${formatMoney(order.tax)} ${cur}</td></tr>`
      : "";

  const paid = order.payment.paidAt != null;

  return `<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Инвойс ${invoiceNo(order.id)}</title>
<style>
  body { font-family: -apple-system, Roboto, Arial, sans-serif; color: #1a1a1a; margin: 24px; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  .muted { color: #666; font-size: 13px; }
  .head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
  table { width: 100%; border-collapse: collapse; margin-top: 12px; }
  th, td { text-align: left; padding: 8px 6px; border-bottom: 1px solid #e0e0e0; font-size: 13px; }
  th.num, td.num { text-align: right; }
  .totals { width: 280px; margin-left: auto; margin-top: 12px; }
  .totals td { border: none; padding: 4px 6px; }
  .grand { font-weight: 700; font-size: 15px; border-top: 2px solid #1a1a1a; }
  .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 12px; }
  .paid { background: #e6f4ea; color: #137333; }
  .due { background: #fce8e6; color: #c5221f; }
</style>
</head>
<body>
  <div class="head">
    <div>
      <h1>${escapeHtml(store.name)}</h1>
      ${contact ? `<div class="muted">${contact}</div>` : ""}
    </div>
    <div style="text-align:right">
      <div><strong>Инвойс №${invoiceNo(order.id)}</strong></div>
      <div class="muted">от ${formatDate(order.createdAt)}</div>
    </div>
  </div>

  <div class="muted">Покупатель: ${escapeHtml(order.customerEmail || "—")}</div>
  ${order.delivery.address ? `<div class="muted">Адрес: ${escapeHtml(order.delivery.address)}</div>` : ""}
  <div class="muted">Оплата: <span class="badge ${paid ? "paid" : "due"}">${paid ? "оплачено" : "к оплате"}</span></div>

  <table>
    <thead>
      <tr><th>Товар</th><th class="num">Кол-во</th><th class="num">Цена</th><th class="num">Сумма</th></tr>
    </thead>
    <tbody>${rows}</tbody>
  </table>

  <table class="totals">
    <tr><td>Подытог</td><td class="num">${formatMoney(order.subtotal)} ${cur}</td></tr>
    ${discountRow}
    ${deliveryRow}
    ${taxRow}
    <tr class="grand"><td>Итого</td><td class="num">${formatMoney(order.total)} ${cur}</td></tr>
  </table>
</body>
</html>`;
}

/** Чтение магазина + рендер инвойса по уже загруженному заказу (доступ — в роуте). */
export async function renderInvoice(storeId: string, order: ApiOrder): Promise<string> {
  const snap = await db().collection("stores").doc(storeId).get();
  const data = snap.data() ?? {};
  const contact = (data["contact"] as Record<string, unknown> | undefined) ?? {};
  const store: InvoiceStore = {
    name: (data["name"] as string) ?? "Wasat Shop",
    contactEmail: (contact["email"] as string | undefined) ?? null,
    contactPhone: (contact["phone"] as string | undefined) ?? null,
  };
  return buildInvoiceHtml(order, store);
}
