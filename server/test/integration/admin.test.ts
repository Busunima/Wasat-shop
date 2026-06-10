import assert from "node:assert/strict";
import { before, test } from "node:test";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "../../src/lib/firebase.ts";
import { listStores, setStoreBlocked, setStorePlan } from "../../src/services/admin.ts";
import { adminStoreListQuerySchema } from "../../src/schemas/admin.ts";
import { ApiError } from "../../src/middleware/errorHandler.ts";

/** Интеграционные тесты суперадмин-операций (FR-S01/S02) против эмулятора Firestore. */

const ADMIN = "superadmin-uid";

function listQuery(overrides: Record<string, unknown> = {}) {
  return adminStoreListQuerySchema.parse(overrides);
}

before(async () => {
  const stores = [
    { id: "s-a", slug: "alpha-shop", name: "Alpha", ownerEmail: "a@example.com", plan: "free", isBlocked: false },
    { id: "s-b", slug: "bravo-shop", name: "Bravo", ownerEmail: "b@example.com", plan: "pro", isBlocked: false },
    { id: "s-c", slug: "charlie-shop", name: "Charlie", ownerEmail: "c@example.com", plan: "free", isBlocked: true },
  ];
  for (const s of stores) {
    await db().collection("stores").doc(s.id).set({
      ...s,
      ownerUid: `owner-${s.id}`,
      currency: "USD",
      isPublic: true,
      createdAt: FieldValue.serverTimestamp(),
    });
  }
});

test("listStores: возвращает все магазины (по убыванию createdAt)", async () => {
  const page = await listStores(listQuery());
  const ids = page.items.map((s) => s.storeId);
  assert.ok(ids.includes("s-a") && ids.includes("s-b") && ids.includes("s-c"));
  // ownerEmail/plan/isBlocked присутствуют в карточке (FR-S01 метрики)
  const a = page.items.find((s) => s.storeId === "s-a")!;
  assert.equal(a.ownerEmail, "a@example.com");
  assert.equal(a.plan, "free");
});

test("listStores: поиск q по slug/email/name/id", async () => {
  assert.deepEqual((await listStores(listQuery({ q: "bravo" }))).items.map((s) => s.storeId), ["s-b"]);
  assert.deepEqual((await listStores(listQuery({ q: "c@example" }))).items.map((s) => s.storeId), ["s-c"]);
  assert.deepEqual((await listStores(listQuery({ q: "s-a" }))).items.map((s) => s.storeId), ["s-a"]);
});

test("listStores: фильтр по plan и blocked", async () => {
  assert.deepEqual(
    (await listStores(listQuery({ plan: "free" }))).items.map((s) => s.storeId).sort(),
    ["s-a", "s-c"],
  );
  assert.deepEqual((await listStores(listQuery({ blocked: "true" }))).items.map((s) => s.storeId), ["s-c"]);
});

test("setStoreBlocked: меняет isBlocked и пишет auditLog", async () => {
  const updated = await setStoreBlocked(ADMIN, "s-a", true, "спам");
  assert.equal(updated.isBlocked, true);

  const audit = await db().collection("auditLog").where("target", "==", "s-a").get();
  const entry = audit.docs.map((d) => d.data()).find((d) => d["action"] === "store.block");
  assert.ok(entry);
  assert.equal(entry!["actorUid"], ADMIN);
  assert.equal((entry!["meta"] as { reason: string }).reason, "спам");

  const back = await setStoreBlocked(ADMIN, "s-a", false, undefined);
  assert.equal(back.isBlocked, false);
});

test("setStorePlan: меняет тариф и пишет auditLog", async () => {
  const updated = await setStorePlan(ADMIN, "s-a", "enterprise");
  assert.equal(updated.plan, "enterprise");

  const audit = await db().collection("auditLog").where("action", "==", "store.plan").get();
  assert.ok(audit.docs.some((d) => d.data()["target"] === "s-a"));
});

test("setStoreBlocked/Plan: несуществующий магазин — NOT_FOUND", async () => {
  await assert.rejects(
    () => setStoreBlocked(ADMIN, "nope", true, undefined),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );
  await assert.rejects(
    () => setStorePlan(ADMIN, "nope", "pro"),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );
});
