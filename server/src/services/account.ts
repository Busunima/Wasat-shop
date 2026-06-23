import { db, auth } from "../lib/firebase.js";
import { logger } from "../lib/logger.js";

/**
 * GDPR (ТЗ §13): экспорт данных покупателя и удаление аккаунта с анонимизацией
 * заказов («право на забвение»). Эндпоинты работают над собственным uid вызывающего.
 * Магазины покупателя выводятся из его заказов (collectionGroup orders по customerUid).
 */

interface ExportedOrder {
  storeId: string | null;
  [key: string]: unknown;
}

/** Выгрузка персональных данных покупателя: профили в магазинах + его заказы. */
export async function exportUserData(uid: string): Promise<{
  uid: string;
  orders: ExportedOrder[];
  customers: unknown[];
}> {
  const ordersSnap = await db().collectionGroup("orders").where("customerUid", "==", uid).get();
  const orders: ExportedOrder[] = ordersSnap.docs.map((d) => ({
    storeId: d.ref.parent.parent?.id ?? null,
    ...d.data(),
  }));
  const storeIds = [...new Set(orders.map((o) => o.storeId).filter((s): s is string => Boolean(s)))];
  const customers: unknown[] = [];
  for (const storeId of storeIds) {
    const snap = await db().collection("stores").doc(storeId).collection("customers").doc(uid).get();
    if (snap.exists) customers.push({ storeId, ...snap.data() });
  }
  return { uid, orders, customers };
}

/**
 * Удаление аккаунта (GDPR §13): заказы анонимизируются (PII вычищается, история
 * сумм сохраняется для учёта магазина), профили покупателя удаляются, затем
 * удаляются users/{uid} и Firebase Auth-пользователь.
 */
export async function deleteUserData(uid: string): Promise<{
  anonymizedOrders: number;
  deletedCustomers: number;
}> {
  const ordersSnap = await db().collectionGroup("orders").where("customerUid", "==", uid).get();
  let anonymizedOrders = 0;
  for (const doc of ordersSnap.docs) {
    await doc.ref.update({
      customerUid: "__deleted__",
      customerEmail: "",
      "delivery.address": null,
    });
    anonymizedOrders += 1;
  }
  const storeIds = [
    ...new Set(ordersSnap.docs.map((d) => d.ref.parent.parent?.id).filter((s): s is string => Boolean(s))),
  ];
  let deletedCustomers = 0;
  for (const storeId of storeIds) {
    const ref = db().collection("stores").doc(storeId).collection("customers").doc(uid);
    if ((await ref.get()).exists) {
      await ref.delete();
      deletedCustomers += 1;
    }
  }
  await db().collection("users").doc(uid).delete().catch(() => undefined);
  await auth().deleteUser(uid).catch(() => undefined);
  logger.info("Аккаунт удалён (GDPR)", { uid, anonymizedOrders, deletedCustomers });
  return { anonymizedOrders, deletedCustomers };
}
