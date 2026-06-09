import { env } from "../config/env.js";
import { logger } from "../lib/logger.js";

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
