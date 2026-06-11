import { z } from "zod";

/**
 * Продуктовые события и аналитика (ТЗ §16, FR-A05).
 * События агрегируются сервером в stores/{storeId}/analytics/{YYYY-MM-DD};
 * сырые GA4/BigQuery — отдельный трек (см. decisions.md, эвристика in-house).
 */

export const ANALYTICS_EVENTS = [
  "product_view",
  "add_to_cart",
  "begin_checkout",
  "purchase",
  "search",
] as const;
export type AnalyticsEventType = (typeof ANALYTICS_EVENTS)[number];

export const analyticsEventSchema = z.object({
  type: z.enum(ANALYTICS_EVENTS),
  productId: z.string().max(64).optional(),
  /** Сумма в минорных единицах (для purchase). */
  value: z.number().int().min(0).optional(),
  qty: z.number().int().min(1).optional(),
  query: z.string().max(120).optional(),
});
export type AnalyticsEvent = z.infer<typeof analyticsEventSchema>;

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export const analyticsQuerySchema = z.object({
  from: z.string().regex(DATE_RE).optional(),
  to: z.string().regex(DATE_RE).optional(),
});
export type AnalyticsQuery = z.infer<typeof analyticsQuerySchema>;

/** Текущая дата UTC в формате YYYY-MM-DD. */
export function todayUtc(now: Date = new Date()): string {
  return now.toISOString().slice(0, 10);
}

/** Дата за N дней до now (UTC), YYYY-MM-DD. */
export function daysAgoUtc(days: number, now: Date = new Date()): string {
  const d = new Date(now);
  d.setUTCDate(d.getUTCDate() - days);
  return d.toISOString().slice(0, 10);
}

/**
 * Список дат YYYY-MM-DD в диапазоне [from, to] включительно (UTC).
 * Ограничен 366 днями (защита от чрезмерных выборок). Pure — под тестом.
 */
export function dateRange(from: string, to: string): string[] {
  const start = new Date(`${from}T00:00:00Z`);
  const end = new Date(`${to}T00:00:00Z`);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || start > end) return [];
  const out: string[] = [];
  const cur = new Date(start);
  while (cur <= end && out.length < 366) {
    out.push(cur.toISOString().slice(0, 10));
    cur.setUTCDate(cur.getUTCDate() + 1);
  }
  return out;
}
