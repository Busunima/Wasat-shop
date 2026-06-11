import { randomUUID } from "node:crypto";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { auth, db } from "../lib/firebase.js";
import { ApiError } from "../middleware/errorHandler.js";
import type { StaffRole } from "../schemas/staff.js";
import { logger } from "../lib/logger.js";

/**
 * Сотрудники магазина (ТЗ §6 FR-A09). Членство хранится в stores/{storeId}/staff/{uid}
 * и зеркалится в Custom Claims пользователя ({storeId, role}) — их ставит ТОЛЬКО сервер
 * (§13). В MVP пользователь привязан к одному магазину (claims держат один storeId).
 * Действия владельца фиксируются в stores/{storeId}/auditLog.
 */

export interface ApiStaffMember {
  uid: string;
  email: string;
  role: string;
  addedAt: number | null;
}

function staffCol(storeId: string) {
  return db().collection("stores").doc(storeId).collection("staff");
}

function toApiStaff(data: FirebaseFirestore.DocumentData): ApiStaffMember {
  const addedAt = data["addedAt"];
  return {
    uid: data["uid"] as string,
    email: (data["email"] as string) ?? "",
    role: (data["role"] as string) ?? "staff",
    addedAt: addedAt instanceof Timestamp ? addedAt.toMillis() : null,
  };
}

async function writeStoreAudit(
  storeId: string,
  actorUid: string,
  action: string,
  target: string,
  meta: Record<string, unknown>,
): Promise<void> {
  await db()
    .collection("stores")
    .doc(storeId)
    .collection("auditLog")
    .doc(randomUUID())
    .set({ actorUid, action, target, meta, at: FieldValue.serverTimestamp() });
}

/** Список сотрудников магазина (FR-A09), новые сверху. */
export async function listStaff(storeId: string): Promise<ApiStaffMember[]> {
  const snap = await staffCol(storeId).orderBy("addedAt", "desc").get();
  return snap.docs.map((doc) => toApiStaff(doc.data()));
}

/**
 * Приглашение сотрудника по email (FR-A09). Пользователь должен быть уже
 * зарегистрирован (вход через Google). Запрещено: добавлять владельца/себя и
 * пользователя, уже привязанного к другому магазину (ограничение одного членства).
 */
export async function addStaff(
  storeId: string,
  actorUid: string,
  email: string,
  role: StaffRole,
): Promise<ApiStaffMember> {
  const storeSnap = await db().collection("stores").doc(storeId).get();
  if (!storeSnap.exists) throw new ApiError("NOT_FOUND", "Магазин не найден");

  let user;
  try {
    user = await auth().getUserByEmail(email);
  } catch {
    throw new ApiError("NOT_FOUND", "Пользователь с таким email не зарегистрирован");
  }

  if (user.uid === actorUid || user.uid === (storeSnap.data()?.["ownerUid"] as string)) {
    throw new ApiError("VALIDATION_ERROR", "Владелец уже управляет магазином");
  }

  const existing = (user.customClaims ?? {}) as Record<string, unknown>;
  const existingStore = existing["storeId"] as string | undefined;
  if (existingStore && existingStore !== storeId) {
    throw new ApiError("CONFLICT", "Пользователь уже привязан к другому магазину");
  }

  await auth().setCustomUserClaims(user.uid, { storeId, role });
  await staffCol(storeId).doc(user.uid).set({
    uid: user.uid,
    email,
    role,
    addedAt: FieldValue.serverTimestamp(),
  });
  await db()
    .collection("users")
    .doc(user.uid)
    .set({ stores: FieldValue.arrayUnion({ storeId, role }) }, { merge: true });
  await writeStoreAudit(storeId, actorUid, "staff.add", user.uid, { email, role });

  logger.info("Сотрудник добавлен", { storeId, role });
  const snap = await staffCol(storeId).doc(user.uid).get();
  return toApiStaff(snap.data()!);
}

/** Смена роли сотрудника (FR-A09). */
export async function updateStaffRole(
  storeId: string,
  actorUid: string,
  uid: string,
  role: StaffRole,
): Promise<ApiStaffMember> {
  const ref = staffCol(storeId).doc(uid);
  const snap = await ref.get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Сотрудник не найден");
  const prevRole = snap.data()?.["role"];

  await auth().setCustomUserClaims(uid, { storeId, role });
  await ref.update({ role });
  // Держим зеркало членства users/{uid}.stores в синхроне (для корректного удаления).
  await db()
    .collection("users")
    .doc(uid)
    .set(
      {
        stores: FieldValue.arrayRemove({ storeId, role: prevRole }),
      },
      { merge: true },
    );
  await db()
    .collection("users")
    .doc(uid)
    .set({ stores: FieldValue.arrayUnion({ storeId, role }) }, { merge: true });
  await writeStoreAudit(storeId, actorUid, "staff.role", uid, { role });

  logger.info("Роль сотрудника изменена", { storeId, role });
  const updated = await ref.get();
  return toApiStaff(updated.data()!);
}

/** Удаление сотрудника (FR-A09) — снимает Custom Claims и членство. */
export async function removeStaff(
  storeId: string,
  actorUid: string,
  uid: string,
): Promise<void> {
  const ref = staffCol(storeId).doc(uid);
  const snap = await ref.get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Сотрудник не найден");

  // Снимаем привязку только если claims указывают именно на этот магазин.
  const user = await auth().getUser(uid);
  const claims = (user.customClaims ?? {}) as Record<string, unknown>;
  if (claims["storeId"] === storeId) {
    await auth().setCustomUserClaims(uid, null);
  }
  await ref.delete();
  await db()
    .collection("users")
    .doc(uid)
    .set({ stores: FieldValue.arrayRemove({ storeId, role: snap.data()?.["role"] }) }, { merge: true });
  await writeStoreAudit(storeId, actorUid, "staff.remove", uid, {});

  logger.info("Сотрудник удалён", { storeId });
}
