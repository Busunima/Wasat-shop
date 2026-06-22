import { FieldValue } from "firebase-admin/firestore";
import { db, messaging } from "../lib/firebase.js";
import { logger } from "../lib/logger.js";

/**
 * Push-уведомления покупателям (ТЗ §6 FR-B10): «снова в наличии» и «цена снижена»
 * для товаров из вишлиста. Токены — stores/{storeId}/fcmTokens/{uid}; адресаты —
 * покупатели с товаром в customers/{uid}.wishlist. Эмулятора FCM не существует,
 * поэтому отправка best-effort: сбой логируется и не влияет на вызвавшую операцию.
 */

export type ProductEventType = "back_in_stock" | "price_drop" | "new_product";

export interface PushPayload {
  title: string;
  body: string;
  data: Record<string, string>;
}

/** Текст уведомления (pure — под unit-тестом). */
export function buildProductNotification(
  type: ProductEventType,
  storeName: string,
  productName: string,
): PushPayload {
  const body =
    type === "back_in_stock"
      ? `«${productName}» снова в наличии`
      : type === "price_drop"
        ? `Цена на «${productName}» снижена`
        : `Новинка: «${productName}»`;
  return { title: storeName, body, data: { type } };
}

/** Подписи статусов заказа для push покупателю (FR-B06). */
const ORDER_STATUS_LABELS: Record<string, string> = {
  NEW: "принят",
  CONFIRMED: "подтверждён",
  PROCESSING: "собирается",
  SHIPPED: "отправлен",
  DELIVERED: "доставлен",
  COMPLETED: "завершён",
  CANCELLED: "отменён",
  RETURN_REQUESTED: "ожидает возврата",
  RETURNED: "возвращён",
  REFUNDED: "возмещён",
};

/** Текст push о смене статуса заказа (pure — под unit-тестом). */
export function buildOrderStatusNotification(
  orderId: string,
  status: string,
  trackingNo?: string | null,
): PushPayload {
  const label = ORDER_STATUS_LABELS[status] ?? status;
  const no = orderId.slice(0, 8).toUpperCase();
  const tracking = status === "SHIPPED" && trackingNo ? `, трек ${trackingNo}` : "";
  return {
    title: `Заказ №${no}`,
    body: `Статус: ${label}${tracking}`,
    data: { type: "order_status", orderId, status },
  };
}

function tokensCol(storeId: string) {
  return db().collection("stores").doc(storeId).collection("fcmTokens");
}

/** Регистрация FCM-токена устройства (идемпотентно: arrayUnion). */
export async function registerPushToken(
  storeId: string,
  uid: string,
  token: string,
  platform: string,
): Promise<void> {
  await tokensCol(storeId)
    .doc(uid)
    .set(
      {
        tokens: FieldValue.arrayUnion(token),
        platform,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
  logger.info("FCM-токен зарегистрирован", { storeId, platform });
}

/**
 * Токены покупателей, у которых товар в вишлисте (адресаты FR-B10).
 * Интеграционно тестируется против эмулятора (без реальной отправки).
 */
export async function collectTokensForProduct(
  storeId: string,
  productId: string,
): Promise<string[]> {
  const customers = await db()
    .collection("stores")
    .doc(storeId)
    .collection("customers")
    .where("wishlist", "array-contains", productId)
    .get();
  if (customers.empty) return [];

  const refs = customers.docs.map((doc) => tokensCol(storeId).doc(doc.id));
  const snaps = await db().getAll(...refs);
  const tokens = new Set<string>();
  for (const snap of snaps) {
    for (const t of (snap.data()?.["tokens"] as string[]) ?? []) tokens.add(t);
  }
  return [...tokens];
}

/**
 * Явные подписчики «снова в наличии» (FR-B10): кнопка «Уведомить о поступлении»
 * пишет productId в customers/{uid}.stockNotifications. Подписка одноразовая —
 * после уведомления снимается (в отличие от постоянного вишлиста).
 */
export async function collectStockSubscriberUids(
  storeId: string,
  productId: string,
): Promise<string[]> {
  const customers = await db()
    .collection("stores")
    .doc(storeId)
    .collection("customers")
    .where("stockNotifications", "array-contains", productId)
    .get();
  return customers.docs.map((doc) => doc.id);
}

/** Токены набора пользователей магазина (объединение, без дублей). */
async function tokensForUids(storeId: string, uids: string[]): Promise<string[]> {
  if (uids.length === 0) return [];
  const snaps = await db().getAll(...uids.map((uid) => tokensCol(storeId).doc(uid)));
  const tokens = new Set<string>();
  for (const snap of snaps) {
    for (const t of (snap.data()?.["tokens"] as string[]) ?? []) tokens.add(t);
  }
  return [...tokens];
}

/** Снять одноразовые подписки после уведомления (batch arrayRemove). */
async function clearStockSubscriptions(
  storeId: string,
  productId: string,
  uids: string[],
): Promise<void> {
  if (uids.length === 0) return;
  const batch = db().batch();
  for (const uid of uids) {
    batch.set(
      db().collection("stores").doc(storeId).collection("customers").doc(uid),
      { stockNotifications: FieldValue.arrayRemove(productId) },
      { merge: true },
    );
  }
  await batch.commit();
}

export interface DeliveryStats {
  targets: number;
  success: number;
  failure: number;
}

/** Best-effort отправка на токены: сбой (нет FCM в dev/test) — лог, не ошибка. */
export async function sendToTokens(
  storeId: string,
  tokens: string[],
  payload: PushPayload,
  data: Record<string, string>,
): Promise<DeliveryStats> {
  if (tokens.length === 0) return { targets: 0, success: 0, failure: 0 };
  try {
    const result = await messaging().sendEachForMulticast({
      tokens,
      notification: { title: payload.title, body: payload.body },
      data: { ...payload.data, ...data },
    });
    logger.info("Push отправлен", {
      storeId,
      success: result.successCount,
      failure: result.failureCount,
    });
    return { targets: tokens.length, success: result.successCount, failure: result.failureCount };
  } catch (err) {
    // Нет FCM-эмулятора/учётных данных (dev/test) — событие фиксируем логом.
    logger.info("Push не отправлен (FCM недоступен)", { storeId, err: String(err) });
    return { targets: tokens.length, success: 0, failure: tokens.length };
  }
}

/** Все токены покупателей магазина (адресаты broadcast-рассылки FR-A07). */
export async function collectAllStoreTokens(storeId: string): Promise<string[]> {
  const snap = await tokensCol(storeId).get();
  const tokens = new Set<string>();
  for (const doc of snap.docs) {
    for (const t of (doc.data()["tokens"] as string[]) ?? []) tokens.add(t);
  }
  return [...tokens];
}

/** Сегмент адресатов рассылки (FR-A07). */
export type BroadcastSegment = "all" | "with_orders" | "no_orders";

/**
 * Токены адресатов broadcast по сегменту (FR-A07). «С заказами»/«без заказов»
 * определяется по наличию uid среди customerUid заказов магазина (ordersCount
 * сервером не ведётся). all — все токены без фильтра.
 */
export async function collectSegmentTokens(
  storeId: string,
  segment: BroadcastSegment,
): Promise<string[]> {
  if (segment === "all") return collectAllStoreTokens(storeId);

  const ordersSnap = await db()
    .collection("stores").doc(storeId).collection("orders")
    .select("customerUid")
    .get();
  const withOrders = new Set<string>();
  for (const doc of ordersSnap.docs) {
    const uid = doc.data()["customerUid"];
    if (typeof uid === "string" && uid) withOrders.add(uid);
  }

  const snap = await tokensCol(storeId).get();
  const tokens = new Set<string>();
  for (const doc of snap.docs) {
    // doc.id == uid покупателя; включаем по совпадению с сегментом.
    if ((segment === "with_orders") === withOrders.has(doc.id)) {
      for (const t of (doc.data()["tokens"] as string[]) ?? []) tokens.add(t);
    }
  }
  return [...tokens];
}

/** Токены конкретного покупателя магазина. */
export async function collectUserTokens(storeId: string, uid: string): Promise<string[]> {
  const snap = await tokensCol(storeId).doc(uid).get();
  return (snap.data()?.["tokens"] as string[]) ?? [];
}

/**
 * Уведомить адресатов о событии товара. Адресаты: вишлист (всегда) + явные
 * подписчики stockNotifications (только back_in_stock, одноразово — подписка
 * снимается после уведомления). Возвращает число целевых токенов; сама отправка
 * FCM — best-effort (нет ключей/эмулятора → лог, не ошибка).
 */
export async function notifyProductEvent(
  storeId: string,
  productId: string,
  type: ProductEventType,
  productName: string,
): Promise<number> {
  const tokens = new Set(await collectTokensForProduct(storeId, productId));
  let subscriberUids: string[] = [];
  if (type === "back_in_stock") {
    subscriberUids = await collectStockSubscriberUids(storeId, productId);
    for (const t of await tokensForUids(storeId, subscriberUids)) tokens.add(t);
  }
  if (tokens.size === 0 && subscriberUids.length === 0) return 0;

  if (tokens.size > 0) {
    const storeSnap = await db().collection("stores").doc(storeId).get();
    const storeName = (storeSnap.data()?.["name"] as string) ?? "Wasat Shop";
    const payload = buildProductNotification(type, storeName, productName);
    await sendToTokens(storeId, [...tokens], payload, { storeId, productId });
  }
  // Одноразовость: подписка выполнена — снимаем независимо от исхода доставки.
  await clearStockSubscriptions(storeId, productId, subscriberUids);
  return tokens.size;
}

/**
 * Уведомить покупателей магазина о новинке (FR-A07: триггер «новый товар»).
 * Адресаты — все покупатели магазина с зарегистрированными токенами (как broadcast).
 * Возвращает число целевых токенов; отправка FCM — best-effort.
 */
export async function notifyNewProduct(
  storeId: string,
  productId: string,
  productName: string,
): Promise<number> {
  const tokens = await collectAllStoreTokens(storeId);
  if (tokens.length === 0) return 0;
  const storeSnap = await db().collection("stores").doc(storeId).get();
  const storeName = (storeSnap.data()?.["name"] as string) ?? "Wasat Shop";
  const payload = buildProductNotification("new_product", storeName, productName);
  await sendToTokens(storeId, tokens, payload, { storeId, productId });
  return tokens.length;
}

/**
 * Push конкретным пользователям магазина (FR-A07: владельцу о новом заказе и т.п.).
 * Возвращает число целевых токенов; отправка best-effort.
 */
export async function sendToUsers(
  storeId: string,
  uids: string[],
  payload: PushPayload,
  data: Record<string, string> = {},
): Promise<number> {
  if (uids.length === 0) return 0;
  const refs = uids.map((uid) => tokensCol(storeId).doc(uid));
  const snaps = await db().getAll(...refs);
  const tokens = new Set<string>();
  for (const snap of snaps) {
    for (const t of (snap.data()?.["tokens"] as string[]) ?? []) tokens.add(t);
  }
  if (tokens.size === 0) return 0;
  await sendToTokens(storeId, [...tokens], payload, { storeId, ...data });
  return tokens.size;
}
