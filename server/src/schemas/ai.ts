import { z } from "zod";

/**
 * AI-ассист контента (ТЗ §6 FR-A12): генерация описания товара по названию,
 * категории, тегам и подсказкам владельца. Env-gated: без ANTHROPIC_API_KEY
 * эндпоинт отвечает NOT_IMPLEMENTED (паттерн Stripe-онбординга).
 */
export const aiDescribeSchema = z.object({
  name: z.string().min(1).max(200),
  category: z.string().max(80).optional(),
  tags: z.array(z.string().max(40)).max(20).default([]),
  /** Свободные подсказки владельца: материал, особенности, аудитория. */
  hints: z.string().max(1000).optional(),
  /** Язык описания (по умолчанию русский — основной язык платформы). */
  language: z.enum(["ru", "en"]).default("ru"),
});
export type AiDescribe = z.infer<typeof aiDescribeSchema>;
