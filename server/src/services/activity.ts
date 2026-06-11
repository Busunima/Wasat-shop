import { FieldValue } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";

/**
 * Активность пользователей (FR-S04, источник MAU). Best-effort отметка времени
 * последнего авторизованного запроса в users/{uid}.lastActiveAt — НЕ блокирует ответ
 * и никогда не бросает. MAU считается как число пользователей с lastActiveAt за 30 дней.
 * На масштабе MVP допустима запись на запрос; троттлинг/отдельный агрегат — позже
 * (см. docs/decisions.md).
 */
export function touchUser(uid: string): void {
  void db()
    .collection("users")
    .doc(uid)
    .set({ lastActiveAt: FieldValue.serverTimestamp() }, { merge: true })
    .catch(() => {
      // намеренно проглатываем — отметка активности не должна влиять на запрос
    });
}
