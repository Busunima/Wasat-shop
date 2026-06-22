import type Stripe from "stripe";
import { env } from "../config/env.js";
import { getStripe } from "./stripe.js";
import { db } from "../lib/firebase.js";
import { ApiError } from "../middleware/errorHandler.js";
import { logger } from "../lib/logger.js";

/**
 * Подписки SaaS владельцев (Stripe Billing, FR-S05/§10.3). Тариф магазина управляется
 * подпиской: checkout создаёт сессию оплаты, вебхук stripe-billing синхронизирует
 * store.plan + store.subscription. Env-gated: без ключа/price — deferred.
 */

const PRICE_BY_PLAN: Record<string, string | undefined> = {
  basic: env.STRIPE_PRICE_BASIC,
  pro: env.STRIPE_PRICE_PRO,
  enterprise: env.STRIPE_PRICE_ENTERPRISE,
};

const APP_BASE = process.env["APP_PUBLIC_URL"] ?? "https://app.example.com";

export type SubscriptionCheckout =
  | { deferred: true; reason: string }
  | { deferred: false; url: string };

/** Сессия оплаты подписки (Stripe Checkout, mode=subscription). */
export async function createSubscriptionCheckout(params: {
  storeId: string;
  ownerEmail: string;
  plan: string;
}): Promise<SubscriptionCheckout> {
  const stripe = getStripe();
  const price = PRICE_BY_PLAN[params.plan];
  if (!stripe || !price) {
    return { deferred: true, reason: "Stripe Billing не сконфигурирован" };
  }
  const metadata = { storeId: params.storeId, plan: params.plan };
  const session = await stripe.checkout.sessions.create({
    mode: "subscription",
    line_items: [{ price, quantity: 1 }],
    customer_email: params.ownerEmail,
    success_url: `${APP_BASE}/billing/success?store=${params.storeId}`,
    cancel_url: `${APP_BASE}/billing/cancel?store=${params.storeId}`,
    metadata,
    subscription_data: { metadata },
  });
  return { deferred: false, url: session.url ?? "" };
}

/** Старт подписки из настроек магазина (FR-S05): берёт ownerEmail из документа. */
export async function startSubscriptionCheckout(
  storeId: string,
  plan: string,
): Promise<SubscriptionCheckout> {
  const snap = await db().collection("stores").doc(storeId).get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Магазин не найден");
  const ownerEmail = (snap.data()!["ownerEmail"] as string | undefined) ?? "";
  return createSubscriptionCheckout({ storeId, ownerEmail, plan });
}

async function patchStoreSubscription(
  storeId: string,
  plan: string,
  status: string,
  extra: Record<string, unknown> = {},
): Promise<void> {
  await db()
    .collection("stores")
    .doc(storeId)
    .set({ plan, subscription: { plan, status, ...extra } }, { merge: true });
  logger.info("Подписка магазина обновлена", { storeId, plan, status });
}

/**
 * Применить событие Stripe Billing к магазину (FR-S05): активация по завершении
 * чекаута, синхронизация статуса (вкл. past_due — grace), даунгрейд до free при отмене.
 * storeId берётся из metadata, проставленной при создании подписки.
 */
export async function applyBillingEvent(event: Stripe.Event): Promise<void> {
  switch (event.type) {
    case "checkout.session.completed": {
      const session = event.data.object;
      if (session.mode !== "subscription") return;
      const storeId = session.metadata?.["storeId"];
      const plan = session.metadata?.["plan"];
      if (!storeId || !plan) return;
      await patchStoreSubscription(storeId, plan, "active", {
        pspCustomerId: typeof session.customer === "string" ? session.customer : null,
      });
      break;
    }
    case "customer.subscription.updated": {
      const sub = event.data.object;
      const storeId = sub.metadata?.["storeId"];
      const plan = sub.metadata?.["plan"];
      if (!storeId || !plan) return;
      // past_due/unpaid — grace: тариф сохраняем, статус сигнализирует проблему оплаты.
      const downgraded = sub.status === "canceled" || sub.status === "unpaid";
      await patchStoreSubscription(storeId, downgraded ? "free" : plan, sub.status);
      break;
    }
    case "customer.subscription.deleted": {
      const sub = event.data.object;
      const storeId = sub.metadata?.["storeId"];
      if (!storeId) return;
      await patchStoreSubscription(storeId, "free", "canceled");
      break;
    }
    default:
      break;
  }
}
