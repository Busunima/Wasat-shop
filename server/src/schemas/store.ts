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

/** Доступные тарифы (ТЗ §8, §10.2). */
export const PLANS = ["free", "basic", "pro", "enterprise"] as const;
export const planSchema = z.enum(PLANS);
export type Plan = z.infer<typeof planSchema>;
