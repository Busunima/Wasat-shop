import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import {
  collectSegmentTokens,
  collectUserTokens,
  sendToTokens,
  type BroadcastSegment,
  type DeliveryStats,
} from "./push.js";
import { logger } from "../lib/logger.js";

/**
 * Рассылки владельца и системные триггеры (ТЗ §6 FR-A07). Broadcast — всем
 * покупателям магазина с зарегистрированными FCM-токенами; «брошенная корзина» —
 * cron сканирует customers с непустой корзиной, не тронутой за порогом времени,
 * и шлёт напоминание один раз на версию корзины. Реальная отправка best-effort
 * (без FCM-эмулятора — лог), всё остальное проверяется против эмулятора.
 */

/** Рассылка покупателям магазина по сегменту (FR-A07). Возвращает статистику доставки. */
export async function broadcastToStore(
  storeId: string,
  title: string,
  body: string,
  segment: BroadcastSegment = "all",
): Promise<DeliveryStats> {
  const storeSnap = await db().collection("stores").doc(storeId).get();
  if (!storeSnap.exists) {
    return { targets: 0, success: 0, failure: 0 };
  }
  const tokens = await collectSegmentTokens(storeId, segment);
  const stats = await sendToTokens(
    storeId,
    tokens,
    { title, body, data: { type: "broadcast" } },
    { storeId },
  );
  logger.info("Broadcast-рассылка", { storeId, ...stats });
  return stats;
}

export interface AbandonedCartReport {
  scanned: number;
  notified: number;
}

/**
 * Напоминание о брошенной корзине (FR-A07, триггер cron). Адресаты: покупатели с
 * непустой корзиной, обновлённой раньше `now - olderThanMs`, которым ещё не слали
 * напоминание для текущей версии корзины (cartReminderSentAt < cartUpdatedAt).
 * Помечает cartReminderSentAt, чтобы не слать повторно. collectionGroup по customers.
 */
export async function runAbandonedCartReminders(
  olderThanMs: number,
  now: number = Date.now(),
): Promise<AbandonedCartReport> {
  const cutoff = Timestamp.fromMillis(now - olderThanMs);
  const snap = await db()
    .collectionGroup("customers")
    .where("cartUpdatedAt", "<=", cutoff)
    .get();

  let notified = 0;
  for (const doc of snap.docs) {
    const data = doc.data();
    const cart = (data["cart"] as unknown[]) ?? [];
    if (cart.length === 0) continue;

    const cartUpdatedAt = data["cartUpdatedAt"] as Timestamp | undefined;
    const sentAt = data["cartReminderSentAt"] as Timestamp | undefined;
    // Уже напоминали для этой версии корзины — пропускаем.
    if (sentAt && cartUpdatedAt && sentAt.toMillis() >= cartUpdatedAt.toMillis()) continue;

    const storeId = doc.ref.parent.parent?.id;
    const uid = doc.id;
    if (!storeId) continue;

    const tokens = await collectUserTokens(storeId, uid);
    if (tokens.length > 0) {
      await sendToTokens(
        storeId,
        tokens,
        { title: "Товары ждут в корзине", body: "Вы забыли оформить заказ", data: { type: "abandoned_cart" } },
        { storeId },
      );
    }
    await doc.ref.set({ cartReminderSentAt: FieldValue.serverTimestamp() }, { merge: true });
    notified += 1;
  }

  logger.info("Напоминания о брошенной корзине", { scanned: snap.size, notified });
  return { scanned: snap.size, notified };
}
