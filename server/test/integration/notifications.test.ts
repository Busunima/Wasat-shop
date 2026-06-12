import assert from "node:assert/strict";
import { before, test } from "node:test";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../../src/lib/firebase.ts";
import { registerPushToken } from "../../src/services/push.ts";
import { broadcastToStore, runAbandonedCartReminders } from "../../src/services/notifications.ts";

/** Интеграционные тесты FR-A07: broadcast-рассылка + триггер брошенной корзины. */

const STORE_ID = "notify-store";

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "notify",
    name: "Notify",
    ownerUid: "owner-notify",
    currency: "USD",
    isPublic: true,
  });
  // Два покупателя с токенами
  await registerPushToken(STORE_ID, "cust-a", "tok-a-00000000", "android");
  await registerPushToken(STORE_ID, "cust-b", "tok-b-00000000", "android");
});

test("broadcastToStore: цели = все токены магазина", async () => {
  const stats = await broadcastToStore(STORE_ID, "Акция", "Скидки −20%");
  assert.equal(stats.targets, 2); // два токена
  // success=0 (нет FCM-эмулятора), но targets корректны и без падения
});

test("broadcastToStore: несуществующий магазин — нули", async () => {
  const stats = await broadcastToStore("ghost-store", "x", "y");
  assert.deepEqual(stats, { targets: 0, success: 0, failure: 0 });
});

test("runAbandonedCartReminders: напоминает по старой непустой корзине один раз", async () => {
  const customers = db().collection("stores").doc(STORE_ID).collection("customers");
  // cust-a: корзина обновлена 2 дня назад → должна напомниться
  await customers.doc("cust-a").set({
    cart: [{ productId: "p1", quantity: 1 }],
    cartUpdatedAt: Timestamp.fromMillis(Date.now() - 2 * 24 * 60 * 60 * 1000),
  });
  // cust-b: корзина свежая (5 мин) → не трогаем
  await customers.doc("cust-b").set({
    cart: [{ productId: "p2", quantity: 1 }],
    cartUpdatedAt: Timestamp.fromMillis(Date.now() - 5 * 60 * 1000),
  });
  // пустая корзина у третьего — не считается
  await customers.doc("cust-c").set({
    cart: [],
    cartUpdatedAt: Timestamp.fromMillis(Date.now() - 3 * 24 * 60 * 60 * 1000),
  });

  const report = await runAbandonedCartReminders(24 * 60 * 60 * 1000);
  assert.equal(report.notified, 1); // только cust-a

  // помечен cartReminderSentAt
  const a = await customers.doc("cust-a").get();
  assert.ok(a.data()?.["cartReminderSentAt"] instanceof Timestamp);

  // повторный прогон — не дублирует (sentAt >= cartUpdatedAt)
  const again = await runAbandonedCartReminders(24 * 60 * 60 * 1000);
  assert.equal(again.notified, 0);
});

test("runAbandonedCartReminders: обновление корзины после напоминания → новое напоминание", async () => {
  const ref = db().collection("stores").doc(STORE_ID).collection("customers").doc("cust-a");
  // корзина изменилась (новее, но всё ещё старше порога) → cartUpdatedAt > sentAt
  await ref.set(
    {
      cart: [{ productId: "p1", quantity: 3 }],
      cartUpdatedAt: Timestamp.fromMillis(Date.now() - 25 * 60 * 60 * 1000),
      cartReminderSentAt: Timestamp.fromMillis(Date.now() - 26 * 60 * 60 * 1000),
    },
    { merge: true },
  );
  const report = await runAbandonedCartReminders(24 * 60 * 60 * 1000);
  assert.ok(report.notified >= 1);
  void FieldValue; // импорт используется косвенно сервисом
});
