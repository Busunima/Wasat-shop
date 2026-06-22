import Stripe from "stripe";
import { env } from "../config/env.js";
import { logger } from "../lib/logger.js";

/** Ленивый клиент Stripe; null без STRIPE_SECRET_KEY (env-gate, ТЗ §10). */
let stripeClient: Stripe | null = null;
export function getStripe(): Stripe | null {
  if (!env.STRIPE_SECRET_KEY) return null;
  if (!stripeClient) stripeClient = new Stripe(env.STRIPE_SECRET_KEY);
  return stripeClient;
}

/**
 * PaymentIntent для оплаты заказа покупателем (FR-B05, §10.1). amountMinor — в
 * минорных единицах валюты магазина. idempotencyKey защищает от дублей при ретраях
 * чекаута; metadata.{storeId,orderId} нужны вебхуку для отметки оплаты. Без ключа
 * Stripe → null (оплата остаётся deferred).
 */
export async function createPaymentIntent(params: {
  amountMinor: number;
  currency: string;
  idempotencyKey: string;
  metadata: Record<string, string>;
  /** Stripe Customer покупателя — для сохранённых карт (FR-B11). */
  customerId?: string | undefined;
}): Promise<{ id: string; clientSecret: string } | null> {
  const stripe = getStripe();
  if (!stripe) return null;
  const intent = await stripe.paymentIntents.create(
    {
      amount: params.amountMinor,
      currency: params.currency.toLowerCase(),
      automatic_payment_methods: { enabled: true },
      metadata: params.metadata,
      // FR-B11: привязать к Customer и сохранить карту для будущих оплат.
      ...(params.customerId
        ? { customer: params.customerId, setup_future_usage: "on_session" as const }
        : {}),
    },
    { idempotencyKey: params.idempotencyKey },
  );
  return { id: intent.id, clientSecret: intent.client_secret ?? "" };
}

/** Версия Stripe API для ephemeral key — должна совпадать с Android SDK (env-override). */
const STRIPE_API_VERSION = process.env["STRIPE_API_VERSION"] ?? "2024-06-20";

/** Создать Stripe Customer покупателя (FR-B11). null без ключа. */
export async function createStripeCustomer(
  email: string,
  metadata: Record<string, string>,
): Promise<string | null> {
  const stripe = getStripe();
  if (!stripe) return null;
  const customer = await stripe.customers.create({ ...(email ? { email } : {}), metadata });
  return customer.id;
}

/** Ephemeral key для PaymentSheet (показ/сохранение карт Customer, FR-B11). null без ключа. */
export async function createEphemeralKey(customerId: string): Promise<string | null> {
  const stripe = getStripe();
  if (!stripe) return null;
  const key = await stripe.ephemeralKeys.create(
    { customer: customerId },
    { apiVersion: STRIPE_API_VERSION },
  );
  return key.secret ?? null;
}

/**
 * Результат запуска онбординга выплат (Stripe Connect Express, ТЗ §10.2).
 * Если ключ Stripe не сконфигурирован — KYC откладывается, магазин работает в режиме
 * черновика без приёма платежей (deferred).
 */
export type OnboardingResult =
  | { deferred: true; reason: string }
  | { deferred: false; stripeAccountId: string; onboardUrl: string };

/** База для return/refresh URL онбординга (Connect). Переопределяется env APP_PUBLIC_URL. */
const ONBOARD_BASE = process.env["APP_PUBLIC_URL"] ?? "https://app.example.com";

/**
 * Создаёт/переиспользует connected-аккаунт Stripe Connect (Express, §10.2) и ссылку
 * онбординга. existingAccountId — если аккаунт уже создан (повторный заход из настроек).
 * Без STRIPE_SECRET_KEY → deferred (магазин в режиме черновика без приёма выплат).
 */
export async function createConnectOnboarding(params: {
  storeId: string;
  ownerEmail: string;
  existingAccountId?: string | undefined;
}): Promise<OnboardingResult> {
  const stripe = getStripe();
  if (!stripe) {
    logger.info("Stripe не сконфигурирован — онбординг выплат отложен", {
      storeId: params.storeId,
    });
    return { deferred: true, reason: "STRIPE_SECRET_KEY не задан" };
  }

  const accountId =
    params.existingAccountId ??
    (
      await stripe.accounts.create({
        type: "express",
        email: params.ownerEmail,
        metadata: { storeId: params.storeId },
      })
    ).id;

  const link = await stripe.accountLinks.create({
    account: accountId,
    refresh_url: `${ONBOARD_BASE}/payouts/refresh?store=${params.storeId}`,
    return_url: `${ONBOARD_BASE}/payouts/return?store=${params.storeId}`,
    type: "account_onboarding",
  });

  return { deferred: false, stripeAccountId: accountId, onboardUrl: link.url };
}
