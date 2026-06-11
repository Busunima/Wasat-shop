import { FieldValue } from "firebase-admin/firestore";
import { db, messaging } from "../lib/firebase.js";
import { logger } from "../lib/logger.js";

/**
 * Push-уведомления покупателям (ТЗ §6 FR-B10): «снова в наличии» и «цена снижена»
 * для товаров из вишлиста. Токены — stores/{storeId}/fcmTokens/{uid}; адресаты —
 * покупатели с товаром в customers/{uid}.wishlist. Эмулятора FCM не существует,
 * поэтому отправка best-effort: сбой логируется и не влияет на вызвавшую операцию.
 */

export type ProductEventType = "back_in_stock" | "price_drop";

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
  const payload =
    type === "back_in_stock"
      ? { title: storeName, body: `«${productName}» снова в наличии` }
      : { title: storeName, body: `Цена на «${productName}» снижена` };
  return { ...payload, data: { type } };
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

/** Best-effort отправка на токены: сбой (нет FCM в dev/test) — лог, не ошибка. */
async function sendToTokens(
  storeId: string,
  tokens: string[],
  payload: PushPayload,
  data: Record<string, string>,
): Promise<void> {
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
  } catch (err) {
    // Нет FCM-эмулятора/учётных данных (dev/test) — событие фиксируем логом.
    logger.info("Push не отправлен (FCM недоступен)", { storeId, err: String(err) });
  }
}

/**
 * Уведомить адресатов о событии товара. Возвращает число целевых токенов;
 * сама отправка FCM — best-effort (нет ключей/эмулятора → лог, не ошибка).
 */
export async function notifyProductEvent(
  storeId: string,
  productId: string,
  type: ProductEventType,
  productName: string,
): Promise<number> {
  const tokens = await collectTokensForProduct(storeId, productId);
  if (tokens.length === 0) return 0;

  const storeSnap = await db().collection("stores").doc(storeId).get();
  const storeName = (storeSnap.data()?.["name"] as string) ?? "Wasat Shop";
  const payload = buildProductNotification(type, storeName, productName);
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
