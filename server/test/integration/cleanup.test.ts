import assert from "node:assert/strict";
import { test } from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { db } from "../../src/lib/firebase.ts";
import { runCleanup } from "../../src/services/cleanup.ts";

/**
 * Интеграционный тест §9 POST /api/cron/cleanup: устаревшие записи журналов
 * (auditLog платформенный/магазинный + inventoryLog) удаляются, свежие остаются;
 * повторный запуск идемпотентен.
 */

const STORE_ID = "cleanup-store";
const DAY_MS = 24 * 60 * 60 * 1000;

function tsDaysAgo(days: number): Timestamp {
  return Timestamp.fromMillis(Date.now() - days * DAY_MS);
}

test("runCleanup: удаляет устаревшие журналы, свежие сохраняет, идемпотентен", async () => {
  const platformAudit = db().collection("auditLog");
  const storeAudit = db().collection("stores").doc(STORE_ID).collection("auditLog");
  const inventoryLog = db().collection("stores").doc(STORE_ID).collection("inventoryLog");

  // Устаревшие (200 дней) — должны удалиться
  await platformAudit.doc("old-1").set({ action: "block", at: tsDaysAgo(200) });
  await storeAudit.doc("old-2").set({ action: "staff_invite", at: tsDaysAgo(200) });
  await inventoryLog.doc("old-3").set({ delta: -1, reason: "sale", at: tsDaysAgo(200) });
  // Свежие (10 дней) — должны остаться
  await platformAudit.doc("fresh-1").set({ action: "plan", at: tsDaysAgo(10) });
  await inventoryLog.doc("fresh-2").set({ delta: 5, reason: "restock", at: tsDaysAgo(10) });

  const result = await runCleanup(180 * DAY_MS);

  assert.equal(result.auditLogDeleted, 2); // платформенный old-1 + магазинный old-2
  assert.equal(result.inventoryLogDeleted, 1); // old-3

  assert.equal((await platformAudit.doc("old-1").get()).exists, false);
  assert.equal((await storeAudit.doc("old-2").get()).exists, false);
  assert.equal((await inventoryLog.doc("old-3").get()).exists, false);
  assert.equal((await platformAudit.doc("fresh-1").get()).exists, true);
  assert.equal((await inventoryLog.doc("fresh-2").get()).exists, true);

  // Идемпотентность: повторный запуск ничего не удаляет
  const again = await runCleanup(180 * DAY_MS);
  assert.equal(again.auditLogDeleted, 0);
  assert.equal(again.inventoryLogDeleted, 0);
});
