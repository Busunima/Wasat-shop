import { z } from "zod";

/**
 * Валидация создания магазина — POST /api/stores/init (ТЗ §9, §4.1 шаг 4).
 * storeId генерируется сервером (UUID), здесь его нет во входе.
 */
export const storeInitSchema = z.object({
  name: z.string().min(1).max(120),
  slug: z
    .string()
    .min(3)
    .max(40)
    .regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/, "slug: строчные латиница/цифры/дефис"),
  currency: z.string().length(3).toUpperCase(), // ISO-4217
  description: z.string().max(2000).optional(),
});

export type StoreInit = z.infer<typeof storeInitSchema>;

/**
 * Опциональное строковое поле формы: отсутствует → undefined (PATCH не трогает),
 * пустая строка/null → null (PATCH очищает), иначе — trim.
 */
const optionalTrimmed = (max: number) =>
  z
    .string()
    .max(max)
    .nullish()
    .transform((v) => (v === undefined ? undefined : v?.trim() ? v.trim() : null));

/** URL-поле формы: "" / null → null (очистка), отсутствие → не трогать. */
const optionalUrl = () =>
  z
    .string()
    .url()
    .or(z.literal(""))
    .nullish()
    .transform((v) => (v === undefined ? undefined : v ? v : null));

const HEX_COLOR = /^#[0-9A-Fa-f]{6}$/;

/**
 * Настройки магазина — PATCH /api/stores/:id (FR-A01).
 * НЕ изменяемые здесь: slug (транзакция с индексом slugs — отдельный эндпоинт позже),
 * currency (цены хранятся в минорных единицах этой валюты — смена запрещена навсегда),
 * plan/isBlocked (суперадмин, FR-S02/S03), stripeAccountId/subscription (вебхуки).
 */
export const storeUpdateSchema = z.object({
  name: z.string().min(1).max(120).optional(),
  description: z.string().max(2000).optional(),
  isPublic: z.boolean().optional(),
  logoUrl: optionalUrl(),
  bannerUrl: optionalUrl(),
  theme: z
    .object({
      primary: z.string().regex(HEX_COLOR, "цвет в формате #RRGGBB"),
      secondary: z.string().regex(HEX_COLOR, "цвет в формате #RRGGBB"),
    })
    .optional(),
  contact: z
    .object({
      email: z.string().email().or(z.literal("")).optional(),
      phone: optionalTrimmed(40),
      address: optionalTrimmed(200),
    })
    .optional(),
  /** Стоимость доставки в минорных единицах валюты магазина; null — не задана. */
  deliveryCost: z.number().int().min(0).nullable().optional(),
  /** Порог «низкого остатка» для push владельцу (FR-A03); null — дефолт сервера. */
  lowStockThreshold: z.number().int().min(0).max(10000).nullable().optional(),
});

/** Порог низкого остатка, если магазин не настроил свой (FR-A03). */
export const DEFAULT_LOW_STOCK_THRESHOLD = 3;

export type StoreUpdate = z.infer<typeof storeUpdateSchema>;

/** Доступные тарифы (ТЗ §8, §10.2). */
export const PLANS = ["free", "basic", "pro", "enterprise"] as const;
export const planSchema = z.enum(PLANS);
export type Plan = z.infer<typeof planSchema>;
