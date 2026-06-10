import { randomUUID } from "node:crypto";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import { ApiError } from "../middleware/errorHandler.js";
import { decodeCursor, encodeCursor } from "../schemas/product.js";
import type { AdminStoreListQuery } from "../schemas/admin.js";
import type { Plan } from "../schemas/store.js";
import { logger } from "../lib/logger.js";

/**
 * Суперадмин-операции (ТЗ §7). Доступ к данным — Admin SDK (Rules игнорируются),
 * действия фиксируются в платформенном auditLog (коллекция верхнего уровня).
 */

/** Карточка магазина для панели суперадмина (включает owner/блокировку/тариф). */
export interface AdminStore {
  storeId: string;
  slug: string;
  name: string;
  ownerUid: string;
  ownerEmail: string;
  currency: string;
  plan: string;
  isPublic: boolean;
  isBlocked: boolean;
  createdAt: number | null;
}

export interface AdminStorePage {
  items: AdminStore[];
  nextCursor: string | null;
}

function toAdminStore(data: FirebaseFirestore.DocumentData): AdminStore {
  const createdAt = data["createdAt"];
  return {
    storeId: data["id"] as string,
    slug: data["slug"] as string,
    name: (data["name"] as string) ?? "",
    ownerUid: (data["ownerUid"] as string) ?? "",
    ownerEmail: (data["ownerEmail"] as string) ?? "",
    currency: (data["currency"] as string) ?? "",
    plan: (data["plan"] as string) ?? "free",
    isPublic: data["isPublic"] === true,
    isBlocked: data["isBlocked"] === true,
    createdAt: createdAt instanceof Timestamp ? createdAt.toMillis() : null,
  };
}

/**
 * Список магазинов (FR-S01). Равенства (plan/isBlocked) — в Firestore с сортировкой
 * по createdAt и курсором; текстовый поиск q — постфильтр в цикле добора (до Algolia,
 * масштаб платформы умеренный; см. decisions.md). Курсор = createdAt+id последнего
 * возвращённого магазина.
 */
export async function listStores(query: AdminStoreListQuery): Promise<AdminStorePage> {
  const { limit } = query;
  const qLower = query.q?.trim().toLowerCase();

  let base: FirebaseFirestore.Query = db().collection("stores");
  if (query.plan !== undefined) base = base.where("plan", "==", query.plan);
  if (query.blocked !== undefined) base = base.where("isBlocked", "==", query.blocked);
  base = base.orderBy("createdAt", "desc").orderBy("id", "desc");

  const cursor = query.cursor ? decodeCursor(query.cursor) : null;
  if (cursor) base = base.startAfter(Timestamp.fromMillis(cursor.v), cursor.id);

  const matches = (s: AdminStore): boolean =>
    !qLower ||
    s.storeId.toLowerCase().includes(qLower) ||
    s.slug.toLowerCase().includes(qLower) ||
    s.ownerEmail.toLowerCase().includes(qLower) ||
    s.name.toLowerCase().includes(qLower);

  const items: AdminStore[] = [];
  let last: { v: number; id: string } | null = null;
  let exhausted = false;
  let pageQuery = base;

  while (items.length < limit && !exhausted) {
    const snap = await pageQuery.limit(50).get();
    exhausted = snap.size < 50;
    for (const doc of snap.docs) {
      const store = toAdminStore(doc.data());
      if (!matches(store)) continue;
      items.push(store);
      last = { v: store.createdAt ?? 0, id: store.storeId };
      if (items.length >= limit) break;
    }
    if (!exhausted && items.length < limit) {
      pageQuery = base.startAfter(snap.docs[snap.size - 1]!);
    }
  }

  return {
    items,
    nextCursor: items.length >= limit && last ? encodeCursor(last) : null,
  };
}

async function writeAuditLog(
  actorUid: string,
  action: string,
  target: string,
  meta: Record<string, unknown>,
): Promise<void> {
  await db()
    .collection("auditLog")
    .doc(randomUUID())
    .set({ actorUid, action, target, meta, at: FieldValue.serverTimestamp() });
}

/** Блокировка/разблокировка магазина (FR-S02) + запись в auditLog. */
export async function setStoreBlocked(
  actorUid: string,
  storeId: string,
  blocked: boolean,
  reason: string | undefined,
): Promise<AdminStore> {
  const ref = db().collection("stores").doc(storeId);
  const snap = await ref.get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Магазин не найден");

  await ref.update({ isBlocked: blocked });
  await writeAuditLog(actorUid, blocked ? "store.block" : "store.unblock", storeId, {
    reason: reason ?? null,
  });
  logger.info("Суперадмин изменил блокировку магазина", { storeId, blocked });

  const updated = await ref.get();
  return toAdminStore(updated.data()!);
}

/** Смена тарифа магазина (FR-S02/S03) + запись в auditLog. */
export async function setStorePlan(
  actorUid: string,
  storeId: string,
  plan: Plan,
): Promise<AdminStore> {
  const ref = db().collection("stores").doc(storeId);
  const snap = await ref.get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Магазин не найден");

  await ref.update({ plan });
  await writeAuditLog(actorUid, "store.plan", storeId, { plan });
  logger.info("Суперадмин сменил тариф магазина", { storeId, plan });

  const updated = await ref.get();
  return toAdminStore(updated.data()!);
}
