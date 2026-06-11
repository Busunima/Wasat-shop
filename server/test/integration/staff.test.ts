import assert from "node:assert/strict";
import { before, test } from "node:test";
import { auth, db } from "../../src/lib/firebase.ts";
import { addStaff, listStaff, removeStaff, updateStaffRole } from "../../src/services/staff.ts";

/** Интеграционные тесты FR-A09: членство сотрудников + Custom Claims (эмулятор Auth). */

const STORE_ID = "staff-store";
const OWNER_UID = "owner-staff";
let staffUid = "";

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "staff",
    name: "Staff",
    ownerUid: OWNER_UID,
    currency: "USD",
    plan: "pro",
    isPublic: true,
  });
  const user = await auth().createUser({ email: "clerk@example.com" });
  staffUid = user.uid;
});

test("addStaff: назначает Custom Claims и пишет членство", async () => {
  const member = await addStaff(STORE_ID, OWNER_UID, "clerk@example.com", "staff");
  assert.equal(member.uid, staffUid);
  assert.equal(member.role, "staff");

  const claims = (await auth().getUser(staffUid)).customClaims ?? {};
  assert.equal(claims["storeId"], STORE_ID);
  assert.equal(claims["role"], "staff");

  const list = await listStaff(STORE_ID);
  assert.ok(list.some((m) => m.uid === staffUid));
});

test("addStaff: незарегистрированный email → NOT_FOUND", async () => {
  await assert.rejects(
    () => addStaff(STORE_ID, OWNER_UID, "nobody@example.com", "staff"),
    /не зарегистрирован/,
  );
});

test("updateStaffRole: меняет роль в claims и документе", async () => {
  const updated = await updateStaffRole(STORE_ID, OWNER_UID, staffUid, "manager");
  assert.equal(updated.role, "manager");
  const claims = (await auth().getUser(staffUid)).customClaims ?? {};
  assert.equal(claims["role"], "manager");
});

test("removeStaff: снимает claims и членство", async () => {
  await removeStaff(STORE_ID, OWNER_UID, staffUid);
  const claims = (await auth().getUser(staffUid)).customClaims ?? {};
  assert.equal(claims["storeId"], undefined);

  const list = await listStaff(STORE_ID);
  assert.equal(list.some((m) => m.uid === staffUid), false);

  await assert.rejects(() => removeStaff(STORE_ID, OWNER_UID, staffUid), /не найден/);
});
