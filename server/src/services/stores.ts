import { randomUUID } from "node:crypto";
import { FieldValue } from "firebase-admin/firestore";
import { auth, db } from "../lib/firebase.js";
import { ApiError } from "../middleware/errorHandler.js";
import type { StoreInit, StoreUpdate } from "../schemas/store.js";
import { createConnectOnboarding, type OnboardingResult } from "./stripe.js";
import { limitsFor, type PlanLimits } from "../config/planLimits.js";
import { logger } from "../lib/logger.js";

export interface CreateStoreResult {
  storeId: string;
  slug: string;
  onboarding: OnboardingResult;
}

/** Публичная карточка магазина (без owner-полей и Firestore-типов). */
export interface ApiStoreInfo {
  storeId: string;
  slug: string;
  name: string;
  description: string;
  currency: string;
  isPublic: boolean;
  logoUrl: string | null;
  bannerUrl: string | null;
  theme: { primary: string; secondary: string } | null;
  contact: { email?: string; phone?: string | null; address?: string | null } | null;
  deliveryCost: number | null;
}

function toApiStoreInfo(data: FirebaseFirestore.DocumentData): ApiStoreInfo {
  return {
    storeId: data["id"] as string,
    slug: data["slug"] as string,
    name: data["name"] as string,
    description: (data["description"] as string) ?? "",
    currency: data["currency"] as string,
    isPublic: data["isPublic"] === true,
    logoUrl: (data["logoUrl"] as string | undefined) ?? null,
    bannerUrl: (data["bannerUrl"] as string | undefined) ?? null,
    theme: (data["theme"] as ApiStoreInfo["theme"]) ?? null,
    contact: (data["contact"] as ApiStoreInfo["contact"]) ?? null,
    deliveryCost: (data["deliveryCost"] as number | undefined) ?? null,
  };
}

/**
 * Инфо магазина для витрины: посетителю — только публичный (isPublic) и не
 * заблокированный; владельцу (includePrivate) — всегда.
 */
export async function getStoreInfo(
  storeId: string,
  includePrivate: boolean,
): Promise<ApiStoreInfo> {
  const snap = await db().collection("stores").doc(storeId).get();
  const data = snap.data();
  if (!data || (!includePrivate && (data["isPublic"] !== true || data["isBlocked"] === true))) {
    throw new ApiError("NOT_FOUND", "Магазин не найден");
  }
  return toApiStoreInfo(data);
}

/** Резолв slug → карточка магазина (FR-B01: deep link / QR). Public-правило видимости. */
export async function resolveSlug(slug: string): Promise<ApiStoreInfo> {
  const slugSnap = await db().collection("slugs").doc(slug).get();
  const storeId = slugSnap.data()?.["storeId"] as string | undefined;
  if (!storeId) throw new ApiError("NOT_FOUND", "Магазин не найден");
  return getStoreInfo(storeId, false);
}

/**
 * Настройки магазина (FR-A01). undefined-поля не трогаются (partial-PATCH),
 * null — явная очистка. slug/currency/plan здесь не изменяются (см. схему).
 */
export async function updateStore(storeId: string, input: StoreUpdate): Promise<ApiStoreInfo> {
  const ref = db().collection("stores").doc(storeId);
  const snap = await ref.get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Магазин не найден");

  const patch: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(input)) {
    if (value !== undefined) patch[key] = value;
  }
  if (Object.keys(patch).length > 0) await ref.update(patch);

  const updated = await ref.get();
  logger.info("Настройки магазина обновлены", { storeId, fields: Object.keys(patch) });
  return toApiStoreInfo(updated.data()!);
}

export interface PlanUsage {
  plan: string;
  limits: PlanLimits;
  usage: { products: number; staff: number };
}

/** Тариф магазина, его лимиты и текущее использование (FR-S03), только владелец. */
export async function getPlanUsage(storeId: string): Promise<PlanUsage> {
  const storeRef = db().collection("stores").doc(storeId);
  const snap = await storeRef.get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Магазин не найден");
  const plan = (snap.data()?.["plan"] as string) ?? "free";

  const [productsCount, staffCount] = await Promise.all([
    storeRef.collection("products").count().get(),
    storeRef.collection("staff").count().get(),
  ]);

  return {
    plan,
    limits: limitsFor(plan),
    usage: { products: productsCount.data().count, staff: staffCount.data().count },
  };
}

/**
 * Создание магазина (ТЗ §4.1 шаг 4, §8). Атомарная транзакция Firestore:
 *  1) уникальность slug (обратный индекс slugs/{slug});
 *  2) документ stores/{storeId} (поля по docs/data-model.md);
 *  3) обратный индекс slugs/{slug} -> storeId и членство users/{uid};
 * затем — Custom Claim владельца и старт онбординга Stripe Connect.
 *
 * storeId — UUID, неизменный (ТЗ §1). Email хранится атрибутом ownerEmail, не в путях.
 */
export async function createStore(
  uid: string,
  ownerEmail: string,
  input: StoreInit,
): Promise<CreateStoreResult> {
  const firestore = db();
  const storeId = randomUUID();

  const slugRef = firestore.collection("slugs").doc(input.slug);
  const storeRef = firestore.collection("stores").doc(storeId);
  const userRef = firestore.collection("users").doc(uid);

  await firestore.runTransaction(async (tx) => {
    const slugSnap = await tx.get(slugRef);
    if (slugSnap.exists) {
      throw new ApiError("CONFLICT", `slug «${input.slug}» уже занят`);
    }

    tx.set(storeRef, {
      id: storeId,
      slug: input.slug,
      ownerUid: uid,
      ownerEmail,
      name: input.name,
      description: input.description ?? "",
      currency: input.currency,
      plan: "free",
      isPublic: false,
      isBlocked: false,
      createdAt: FieldValue.serverTimestamp(),
    });

    tx.set(slugRef, { storeId });

    tx.set(
      userRef,
      { stores: FieldValue.arrayUnion({ storeId, role: "owner" }) },
      { merge: true },
    );
  });

  // Custom Claim владельца — задаёт ТОЛЬКО сервер (ТЗ §13).
  await auth().setCustomUserClaims(uid, { storeId, role: "owner" });

  const onboarding = await createConnectOnboarding({ storeId, ownerEmail });

  logger.info("Магазин создан", { storeId, slug: input.slug });
  return { storeId, slug: input.slug, onboarding };
}
