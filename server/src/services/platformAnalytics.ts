import { Timestamp } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import {
  daysAgoUtc,
  todayUtc,
  type AnalyticsQuery,
} from "../schemas/analytics.js";
import { PLANS, type Plan } from "../schemas/store.js";

/**
 * Глобальная аналитика платформы (ТЗ §7 FR-S04) для суперадмина: GMV, заказы, средний
 * чек, число и состав магазинов, распределение по тарифам, топ-магазины, дневной тренд
 * и MAU (по users.lastActiveAt). Доступ — Admin SDK; чтение поверх per-store агрегатов
 * stores/{id}/analytics/{date} (FR-A05) и коллекции stores.
 */

/** Поля дневного агрегата, нужные для сводки (см. services/analytics.ts). */
export interface DailyAnalytics {
  views?: number;
  addToCarts?: number;
  checkouts?: number;
  purchases?: number;
  revenue?: number;
  searches?: number;
}

export interface AnalyticsRow {
  storeId: string;
  date: string;
  data: DailyAnalytics;
}

export interface StoreMeta {
  name: string;
  slug: string;
}

export interface PlatformSummary {
  gmv: number;
  orders: number;
  /** Средний чек в минорных единицах (gmv/orders), 0 если заказов нет. */
  avgCheck: number;
  searches: number;
  funnel: { views: number; addToCarts: number; checkouts: number; purchases: number };
  topStores: Array<{ storeId: string; name: string; slug: string; gmv: number; orders: number }>;
  daily: Array<{ date: string; gmv: number; orders: number }>;
}

/**
 * Чистая свёртка дневных агрегатов всех магазинов за период. Детерминирована,
 * под unit-тестом. topStores — по GMV (desc), daily — по дате (asc).
 */
export function summarizeAnalyticsDocs(
  rows: AnalyticsRow[],
  stores: Map<string, StoreMeta>,
): PlatformSummary {
  const funnel = { views: 0, addToCarts: 0, checkouts: 0, purchases: 0 };
  let gmv = 0;
  let searches = 0;
  const perStore = new Map<string, { gmv: number; orders: number }>();
  const perDay = new Map<string, { gmv: number; orders: number }>();

  for (const { storeId, date, data } of rows) {
    const revenue = data.revenue ?? 0;
    const purchases = data.purchases ?? 0;
    funnel.views += data.views ?? 0;
    funnel.addToCarts += data.addToCarts ?? 0;
    funnel.checkouts += data.checkouts ?? 0;
    funnel.purchases += purchases;
    gmv += revenue;
    searches += data.searches ?? 0;

    const s = perStore.get(storeId) ?? { gmv: 0, orders: 0 };
    s.gmv += revenue;
    s.orders += purchases;
    perStore.set(storeId, s);

    const d = perDay.get(date) ?? { gmv: 0, orders: 0 };
    d.gmv += revenue;
    d.orders += purchases;
    perDay.set(date, d);
  }

  const topStores = [...perStore.entries()]
    .map(([storeId, v]) => ({
      storeId,
      name: stores.get(storeId)?.name ?? "",
      slug: stores.get(storeId)?.slug ?? "",
      gmv: v.gmv,
      orders: v.orders,
    }))
    .sort((a, b) => b.gmv - a.gmv)
    .slice(0, 10);

  const daily = [...perDay.entries()]
    .map(([date, v]) => ({ date, gmv: v.gmv, orders: v.orders }))
    .sort((a, b) => a.date.localeCompare(b.date));

  return {
    gmv,
    orders: funnel.purchases,
    avgCheck: funnel.purchases > 0 ? Math.round(gmv / funnel.purchases) : 0,
    searches,
    funnel,
    topStores,
    daily,
  };
}

export interface PlatformReport extends PlatformSummary {
  from: string;
  to: string;
  /** Активные пользователи за последние 30 дней (по users.lastActiveAt). */
  mau: number;
  stores: {
    total: number;
    public: number;
    blocked: number;
    byPlan: Record<Plan, number>;
  };
}

function emptyByPlan(): Record<Plan, number> {
  return PLANS.reduce((acc, p) => ({ ...acc, [p]: 0 }), {} as Record<Plan, number>);
}

/** Дашборд платформы за период (FR-S04). По умолчанию — последние 30 дней. */
export async function getPlatformAnalytics(query: AnalyticsQuery): Promise<PlatformReport> {
  const to = query.to ?? todayUtc();
  const from = query.from ?? daysAgoUtc(29);
  const inRange = (date: string): boolean => date >= from && date <= to;

  // Магазины: карта имён + счётчики состава и тарифов.
  const storesSnap = await db().collection("stores").get();
  const storeMap = new Map<string, StoreMeta>();
  const stores = { total: 0, public: 0, blocked: 0, byPlan: emptyByPlan() };
  for (const doc of storesSnap.docs) {
    const data = doc.data();
    storeMap.set(doc.id, {
      name: (data["name"] as string) ?? "",
      slug: (data["slug"] as string) ?? "",
    });
    stores.total += 1;
    if (data["isPublic"] === true && data["isBlocked"] !== true) stores.public += 1;
    if (data["isBlocked"] === true) stores.blocked += 1;
    const plan = (data["plan"] as Plan) ?? "free";
    if (plan in stores.byPlan) stores.byPlan[plan] += 1;
  }

  // Дневные агрегаты всех магазинов (collectionGroup), фильтр по дате-id в диапазоне.
  const analyticsSnap = await db().collectionGroup("analytics").get();
  const rows: AnalyticsRow[] = [];
  for (const doc of analyticsSnap.docs) {
    const date = doc.id;
    if (!inRange(date)) continue;
    const storeId = doc.ref.parent.parent?.id;
    if (!storeId) continue;
    rows.push({ storeId, date, data: doc.data() as DailyAnalytics });
  }

  const summary = summarizeAnalyticsDocs(rows, storeMap);

  // MAU: пользователи с активностью за последние 30 дней.
  const since = Timestamp.fromMillis(Date.now() - 30 * 24 * 60 * 60 * 1000);
  const mauSnap = await db()
    .collection("users")
    .where("lastActiveAt", ">=", since)
    .count()
    .get();

  return { from, to, ...summary, mau: mauSnap.data().count, stores };
}
