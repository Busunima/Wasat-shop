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
}): Promise<{ id: string; clientSecret: string } | null> {
  const stripe = getStripe();
  if (!stripe) return null;
  const intent = await stripe.paymentIntents.create(
    {
      amount: params.amountMinor,
      currency: params.currency.toLowerCase(),
      automatic_payment_methods: { enabled: true },
      metadata: params.metadata,
    },
    { idempotencyKey: params.idempotencyKey },
  );
  return { id: intent.id, clientSecret: intent.client_secret ?? "" };
}

/**
 * Результат запуска онбординга выплат (Stripe Connect Express, ТЗ §10.2).
 * Если ключ Stripe не сконфигурирован — KYC откладывается, магазин работает в режиме
 * черновика без приёма платежей (deferred).
 */
export type OnboardingResult =
  | { deferred: true; reason: string }
  | { deferred: false; stripeAccountId: string; onboardUrl: string };

/**
 * Создаёт connected-аккаунт Stripe Connect (тип Express) и ссылку онбординга.
 * За env-guard: реальные вызовы Stripe SDK подключаются в Фазе 4, когда настроены ключи.
 * Сейчас при наличии ключа — точка интеграции (TODO), без ключа — deferred.
 */
export async function createConnectOnboarding(params: {
  storeId: string;
  ownerEmail: string;
}): Promise<OnboardingResult> {
  if (!env.STRIPE_SECRET_KEY) {
    logger.info("Stripe не сконфигурирован — онбординг выплат отложен", {
      storeId: params.storeId,
    });
    return { deferred: true, reason: "STRIPE_SECRET_KEY не задан" };
  }

  // TODO(Фаза 4): stripe.accounts.create({ type: 'express', email })
  //   + stripe.accountLinks.create({ account, type: 'account_onboarding', ... }).
  logger.warn("createConnectOnboarding: интеграция Stripe реализуется в Фазе 4", {
    storeId: params.storeId,
  });
  return { deferred: true, reason: "Интеграция Stripe реализуется в Фазе 4" };
}
