import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import { deleteUserData, exportUserData } from "../../src/services/account.ts";

const STORE_ID = "acct-store";
const UID = "buyer-acct-gdpr";

before(async () => {
  await db().collection("stores").doc(STORE_ID).collection("orders").doc("o-gdpr").set({
    id: "o-gdpr",
    customerUid: UID,
    customerEmail: "b@example.com",
    delivery: { method: "courier", address: "Street 1" },
    total: 5000,
  });
  await db()
    .collection("stores")
    .doc(STORE_ID)
    .collection("customers")
    .doc(UID)
    .set({ email: "b@example.com", wishlist: ["p1"] });
});

test("GDPR export §13: возвращает заказы и профиль покупателя", async () => {
  const data = await exportUserData(UID);
  assert.equal(data.uid, UID);
  assert.equal(data.orders.length, 1);
  assert.equal(data.customers.length, 1);
});

test("GDPR delete §13: анонимизирует заказы и удаляет профиль", async () => {
  const res = await deleteUserData(UID);
  assert.equal(res.anonymizedOrders, 1);
  assert.equal(res.deletedCustomers, 1);

  const order = (
    await db().collection("stores").doc(STORE_ID).collection("orders").doc("o-gdpr").get()
  ).data();
  assert.equal(order?.["customerUid"], "__deleted__");
  assert.equal(order?.["customerEmail"], "");
  assert.equal((order?.["delivery"] as Record<string, unknown>)["address"], null);

  const customer = await db()
    .collection("stores")
    .doc(STORE_ID)
    .collection("customers")
    .doc(UID)
    .get();
  assert.equal(customer.exists, false);
});
