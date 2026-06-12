import { Timestamp } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import { logger } from "../lib/logger.js";

/**
 * Очистка устаревших данных (ТЗ §9 POST /api/cron/cleanup, §15). Append-only
 * журналы (auditLog — платформенный и stores/{id}/auditLog; inventoryLog) растут
 * без ограничений; cron удаляет записи старше окна хранения. Операция идемпотентна
 * (повторный запуск ничего не удаляет) и не затрагивает бизнес-состояние.
 */

export interface CleanupResult {
  auditLogDeleted: number;
  inventoryLogDeleted: number;
}

/** Окно хранения журналов по умолчанию — 180 дней. */
export const DEFAULT_RETENTION_MS = 180 * 24 * 60 * 60 * 1000;

const BATCH_SIZE = 300;

/**
 * Удалить документы collection-group старше cutoff по полю `at` (батчами).
 * collectionGroup("auditLog") покрывает и платформенную коллекцию, и под-коллекции
 * магазинов; collectionGroup("inventoryLog") — журналы остатков всех магазинов.
 */
async function deleteOlderThan(group: string, cutoff: Timestamp): Promise<number> {
  let deleted = 0;
  for (;;) {
    const snap = await db()
      .collectionGroup(group)
      .where("at", "<", cutoff)
      .limit(BATCH_SIZE)
      .get();
    if (snap.empty) break;
    const batch = db().batch();
    for (const doc of snap.docs) batch.delete(doc.ref);
    await batch.commit();
    deleted += snap.size;
  }
  return deleted;
}

export async function runCleanup(
  retentionMs: number = DEFAULT_RETENTION_MS,
): Promise<CleanupResult> {
  const cutoff = Timestamp.fromMillis(Date.now() - retentionMs);
  const auditLogDeleted = await deleteOlderThan("auditLog", cutoff);
  const inventoryLogDeleted = await deleteOlderThan("inventoryLog", cutoff);
  logger.info("Cleanup устаревших журналов завершён", {
    auditLogDeleted,
    inventoryLogDeleted,
    retentionMs,
  });
  return { auditLogDeleted, inventoryLogDeleted };
}
