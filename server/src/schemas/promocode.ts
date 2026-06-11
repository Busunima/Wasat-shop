import { z } from "zod";

/**
 * Промокоды (ТЗ §6 FR-A06). Типы скидок: фикс. сумма / процент / бесплатная доставка.
 * Серверная проверка применяется при чекауте (Фаза 4) — логика applyPromo переиспользуется.
 */

export const PROMO_TYPES = ["fixed", "percent", "free_shipping"] as const;
export type PromoType = (typeof PROMO_TYPES)[number];

const codeSchema = z
  .string()
  .min(3)
  .max(32)
  .regex(/^[A-Z0-9][A-Z0-9-]*$/, "код: A-Z, 0-9, дефис");

export const promoScopeSchema = z
  .object({
    productIds: z.array(z.string().max(64)).max(200).optional(),
    categories: z.array(z.string().max(80)).max(50).optional(),
  })
  .optional();

export const promoCreateSchema = z
  .object({
    code: codeSchema,
    type: z.enum(PROMO_TYPES),
    /** fixed — минорные единицы; percent — 1..100; free_shipping — игнор. */
    value: z.number().int().min(0).default(0),
    minAmount: z.number().int().min(0).default(0),
    /** ISO datetime; null/absent — без границы. */
    startsAt: z.string().datetime().nullish(),
    expiresAt: z.string().datetime().nullish(),
    usageLimit: z.number().int().min(1).nullish(),
    scope: promoScopeSchema,
    active: z.boolean().default(true),
  })
  .refine((p) => p.type !== "percent" || (p.value >= 1 && p.value <= 100), {
    message: "percent: value должен быть 1..100",
    path: ["value"],
  });
export type PromoCreate = z.infer<typeof promoCreateSchema>;

export const promoUpdateSchema = promoCreateSchema.innerType().partial().omit({ code: true });

// ── Чистая логика применения (переиспользуется чекаутом, под unit-тестами) ──

export interface PromoEvaluable {
  type: PromoType;
  value: number;
  minAmount: number;
  startsAtMs: number | null;
  expiresAtMs: number | null;
  usageLimit: number | null;
  usedCount: number;
  scope: { productIds?: string[]; categories?: string[] } | null;
  active: boolean;
}

export interface PromoContext {
  subtotal: number;
  itemProductIds: string[];
  itemCategories: string[];
  nowMs: number;
}

export interface PromoResult {
  valid: boolean;
  /** Скидка в минорных единицах (0 для free_shipping — доставка обнуляется отдельно). */
  discount: number;
  freeShipping: boolean;
  reason?: string;
}

function scopeMatches(
  scope: PromoEvaluable["scope"],
  ctx: PromoContext,
): boolean {
  if (!scope || (!scope.productIds?.length && !scope.categories?.length)) return true;
  const byProduct = scope.productIds?.some((id) => ctx.itemProductIds.includes(id)) ?? false;
  const byCategory = scope.categories?.some((c) => ctx.itemCategories.includes(c)) ?? false;
  return byProduct || byCategory;
}

/**
 * Оценка промокода для корзины. valid=false с reason при любом нарушении
 * (неактивен/даты/лимит/мин. сумма/scope). Pure — детерминирована по nowMs.
 */
export function applyPromo(promo: PromoEvaluable, ctx: PromoContext): PromoResult {
  const fail = (reason: string): PromoResult => ({ valid: false, discount: 0, freeShipping: false, reason });

  if (!promo.active) return fail("Промокод неактивен");
  if (promo.startsAtMs !== null && ctx.nowMs < promo.startsAtMs) return fail("Промокод ещё не действует");
  if (promo.expiresAtMs !== null && ctx.nowMs > promo.expiresAtMs) return fail("Срок действия истёк");
  if (promo.usageLimit !== null && promo.usedCount >= promo.usageLimit) return fail("Исчерпан лимит применений");
  if (ctx.subtotal < promo.minAmount) return fail("Не достигнута минимальная сумма");
  if (!scopeMatches(promo.scope, ctx)) return fail("Промокод не применим к товарам в корзине");

  switch (promo.type) {
    case "fixed":
      return { valid: true, discount: Math.min(promo.value, ctx.subtotal), freeShipping: false };
    case "percent":
      return {
        valid: true,
        discount: Math.floor((ctx.subtotal * promo.value) / 100),
        freeShipping: false,
      };
    case "free_shipping":
      return { valid: true, discount: 0, freeShipping: true };
  }
}
