import { FieldValue } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import {
  daysAgoUtc,
  dateRange,
  todayUtc,
  type AnalyticsEvent,
  type AnalyticsQuery,
} from "../schemas/analytics.js";

/**
 * Аналитика (ТЗ §16, FR-A05). Продуктовые события инкрементируют дневной агрегат
 * stores/{storeId}/analytics/{date}; дашборд читает диапазон и считает воронку,
 * выручку, средний чек и топ-товары. Запись — только сервер (Admin SDK).
 */

function analyticsDoc(storeId: string, date: string) {
  return db().collection("stores").doc(storeId).collection("analytics").doc(date);
}

/** Запись продуктового события в дневной агрегат (атомарные инкременты). */
export async function recordEvent(storeId: string, event: AnalyticsEvent): Promise<void> {
  const ref = analyticsDoc(storeId, todayUtc());
  const update: Record<string, unknown> = {};

  switch (event.type) {
    case "product_view":
      update["views"] = FieldValue.increment(1);
      if (event.productId) {
        // set+merge: точечные пути НЕ трактуются как field path (это делает update()),
        // поэтому пишем вложенным объектом — глубокий merge сложит счётчики.
        update["productViews"] = { [event.productId]: FieldValue.increment(1) };
      }
      break;
    case "add_to_cart":
      update["addToCarts"] = FieldValue.increment(1);
      break;
    case "begin_checkout":
      update["checkouts"] = FieldValue.increment(1);
      break;
    case "purchase":
      update["purchases"] = FieldValue.increment(1);
      update["revenue"] = FieldValue.increment(event.value ?? 0);
      break;
    case "search":
      update["searches"] = FieldValue.increment(1);
      break;
  }
  await ref.set(update, { merge: true });
}

export interface AnalyticsFunnel {
  views: number;
  addToCarts: number;
  checkouts: number;
  purchases: number;
}

export interface AnalyticsReport {
  from: string;
  to: string;
  revenue: number;
  orders: number;
  /** Средний чек в минорных единицах (revenue/orders), 0 если заказов нет. */
  avgCheck: number;
  searches: number;
  funnel: AnalyticsFunnel;
  /** Конверсии воронки в процентах (0..100). */
  conversion: { viewToCart: number; cartToOrder: number; viewToOrder: number };
  topProducts: Array<{ productId: string; views: number }>;
  daily: Array<{ date: string; views: number; orders: number; revenue: number }>;
}

function pct(part: number, whole: number): number {
  return whole > 0 ? Math.round((part / whole) * 1000) / 10 : 0;
}

/** Дашборд за период (FR-A05). По умолчанию — последние 30 дней. */
export async function getAnalytics(
  storeId: string,
  query: AnalyticsQuery,
): Promise<AnalyticsReport> {
  const to = query.to ?? todayUtc();
  const from = query.from ?? daysAgoUtc(29);
  const dates = dateRange(from, to);

  const funnel: AnalyticsFunnel = { views: 0, addToCarts: 0, checkouts: 0, purchases: 0 };
  let revenue = 0;
  let searches = 0;
  const productViews = new Map<string, number>();
  const daily: AnalyticsReport["daily"] = [];

  // Батч-чтение документов дней (getAll по ссылкам).
  const refs = dates.map((d) => analyticsDoc(storeId, d));
  const snaps = refs.length > 0 ? await db().getAll(...refs) : [];

  snaps.forEach((snap, i) => {
    const data = snap.data() ?? {};
    const views = (data["views"] as number) ?? 0;
    const addToCarts = (data["addToCarts"] as number) ?? 0;
    const checkouts = (data["checkouts"] as number) ?? 0;
    const purchases = (data["purchases"] as number) ?? 0;
    const dayRevenue = (data["revenue"] as number) ?? 0;

    funnel.views += views;
    funnel.addToCarts += addToCarts;
    funnel.checkouts += checkouts;
    funnel.purchases += purchases;
    revenue += dayRevenue;
    searches += (data["searches"] as number) ?? 0;

    const pv = (data["productViews"] as Record<string, number>) ?? {};
    for (const [pid, count] of Object.entries(pv)) {
      productViews.set(pid, (productViews.get(pid) ?? 0) + count);
    }

    daily.push({ date: dates[i]!, views, orders: purchases, revenue: dayRevenue });
  });

  const topProducts = [...productViews.entries()]
    .map(([productId, views]) => ({ productId, views }))
    .sort((a, b) => b.views - a.views)
    .slice(0, 10);

  return {
    from,
    to,
    revenue,
    orders: funnel.purchases,
    avgCheck: funnel.purchases > 0 ? Math.round(revenue / funnel.purchases) : 0,
    searches,
    funnel,
    conversion: {
      viewToCart: pct(funnel.addToCarts, funnel.views),
      cartToOrder: pct(funnel.purchases, funnel.addToCarts),
      viewToOrder: pct(funnel.purchases, funnel.views),
    },
    topProducts,
    daily,
  };
}
