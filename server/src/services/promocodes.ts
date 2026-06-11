import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import { ApiError } from "../middleware/errorHandler.js";
import {
  applyPromo,
  type PromoCreate,
  type PromoEvaluable,
  type PromoResult,
  type PromoType,
} from "../schemas/promocode.js";
import { logger } from "../lib/logger.js";

/**
 * Промокоды (ТЗ §6 FR-A06): CRUD в stores/{storeId}/promocodes/{code} и публичный
 * предпросмотр скидки для корзины. Чистая логика — в schemas/promocode.applyPromo
 * (переиспользуется чекаутом Фазы 4, где usedCount инкрементируется транзакционно).
 * Документ id = код (uppercase) → уникальность кода даётся бесплатно.
 */

export interface ApiPromo {
  code: string;
  type: PromoType;
  value: number;
  minAmount: number;
  startsAt: string | null;
  expiresAt: string | null;
  usageLimit: number | null;
  usedCount: number;
  scope: { productIds?: string[]; categories?: string[] } | null;
  active: boolean;
}

function promosCol(storeId: string) {
  return db().collection("stores").doc(storeId).collection("promocodes");
}

function tsToIso(value: unknown): string | null {
  return value instanceof Timestamp ? value.toDate().toISOString() : null;
}

function tsToMs(value: unknown): number | null {
  return value instanceof Timestamp ? value.toMillis() : null;
}

function toApiPromo(data: FirebaseFirestore.DocumentData): ApiPromo {
  return {
    code: data["code"] as string,
    type: data["type"] as PromoType,
    value: (data["value"] as number) ?? 0,
    minAmount: (data["minAmount"] as number) ?? 0,
    startsAt: tsToIso(data["startsAt"]),
    expiresAt: tsToIso(data["expiresAt"]),
    usageLimit: (data["usageLimit"] as number | undefined) ?? null,
    usedCount: (data["usedCount"] as number) ?? 0,
    scope: (data["scope"] as ApiPromo["scope"]) ?? null,
    active: (data["active"] as boolean) ?? false,
  };
}

async function assertStoreExists(storeId: string): Promise<void> {
  const snap = await db().collection("stores").doc(storeId).get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Магазин не найден");
}

/** Создание промокода (FR-A06). Код уникален в пределах магазина (id документа). */
export async function createPromocode(storeId: string, input: PromoCreate): Promise<ApiPromo> {
  await assertStoreExists(storeId);
  const ref = promosCol(storeId).doc(input.code);

  const docData = {
    code: input.code,
    type: input.type,
    value: input.value,
    minAmount: input.minAmount,
    startsAt: input.startsAt ? Timestamp.fromDate(new Date(input.startsAt)) : null,
    expiresAt: input.expiresAt ? Timestamp.fromDate(new Date(input.expiresAt)) : null,
    usageLimit: input.usageLimit ?? null,
    usedCount: 0,
    scope: input.scope ?? null,
    active: input.active,
    createdAt: FieldValue.serverTimestamp(),
  };

  await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (snap.exists) throw new ApiError("CONFLICT", "Промокод с таким кодом уже есть");
    tx.set(ref, docData);
  });

  logger.info("Промокод создан", { storeId, code: input.code });
  return toApiPromo(docData);
}

/** Частичное обновление промокода (PATCH: absent → не трогать, "" недопустимо в схеме). */
export async function updatePromocode(
  storeId: string,
  code: string,
  patch: Record<string, unknown>,
): Promise<ApiPromo> {
  const ref = promosCol(storeId).doc(code);

  await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new ApiError("NOT_FOUND", "Промокод не найден");

    const update: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(patch)) {
      if (value === undefined) continue;
      if ((key === "startsAt" || key === "expiresAt") && typeof value === "string") {
        update[key] = Timestamp.fromDate(new Date(value));
      } else {
        update[key] = value;
      }
    }
    tx.update(ref, update);
  });

  const updated = await ref.get();
  return toApiPromo(updated.data()!);
}

export async function deletePromocode(storeId: string, code: string): Promise<void> {
  const ref = promosCol(storeId).doc(code);
  const snap = await ref.get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Промокод не найден");
  await ref.delete();
  logger.info("Промокод удалён", { storeId, code });
}

/** Список промокодов магазина (FR-A06), новые сверху. */
export async function listPromocodes(storeId: string): Promise<ApiPromo[]> {
  const snap = await promosCol(storeId).orderBy("createdAt", "desc").get();
  return snap.docs.map((doc) => toApiPromo(doc.data()));
}

export interface PromoPreviewInput {
  subtotal: number;
  itemProductIds: string[];
  itemCategories: string[];
}

/**
 * Публичный предпросмотр промокода для корзины (FR-A06 / FR-B04). Не инкрементирует
 * usedCount — лимит лишь проверяется; фактическое списание — при оформлении (Фаза 4).
 * Несуществующий код → valid=false (не 404), чтобы UI единообразно показал причину.
 */
export async function previewPromo(
  storeId: string,
  code: string,
  input: PromoPreviewInput,
): Promise<PromoResult & { code: string }> {
  const snap = await promosCol(storeId).doc(code).get();
  if (!snap.exists) {
    return { code, valid: false, discount: 0, freeShipping: false, reason: "Промокод не найден" };
  }
  const data = snap.data()!;
  const evaluable: PromoEvaluable = {
    type: data["type"] as PromoType,
    value: (data["value"] as number) ?? 0,
    minAmount: (data["minAmount"] as number) ?? 0,
    startsAtMs: tsToMs(data["startsAt"]),
    expiresAtMs: tsToMs(data["expiresAt"]),
    usageLimit: (data["usageLimit"] as number | undefined) ?? null,
    usedCount: (data["usedCount"] as number) ?? 0,
    scope: (data["scope"] as PromoEvaluable["scope"]) ?? null,
    active: (data["active"] as boolean) ?? false,
  };

  const result = applyPromo(evaluable, {
    subtotal: input.subtotal,
    itemProductIds: input.itemProductIds,
    itemCategories: input.itemCategories,
    nowMs: Date.now(),
  });
  return { code, ...result };
}
