import { randomUUID } from "node:crypto";
import { FieldValue } from "firebase-admin/firestore";
import { auth, db } from "../lib/firebase.js";
import { ApiError } from "../middleware/errorHandler.js";
import type { StoreInit } from "../schemas/store.js";
import { createConnectOnboarding, type OnboardingResult } from "./stripe.js";
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
  return {
    storeId: data["id"] as string,
    slug: data["slug"] as string,
    name: data["name"] as string,
    description: (data["description"] as string) ?? "",
    currency: data["currency"] as string,
    isPublic: data["isPublic"] === true,
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
