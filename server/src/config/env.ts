import { z } from "zod";

/**
 * Валидация окружения через Zod (ТЗ §13: двойная валидация, секреты без fallback в prod).
 * В production отсутствие критичных секретов — фатальная ошибка на старте.
 */
const baseSchema = z.object({
  NODE_ENV: z.enum(["development", "staging", "production"]).default("development"),
  PORT: z.coerce.number().int().positive().default(8080),
  GOOGLE_CLOUD_PROJECT: z.string().min(1).optional(),

  STRIPE_SECRET_KEY: z.string().optional(),
  STRIPE_WEBHOOK_SECRET: z.string().optional(),
  STRIPE_BILLING_WEBHOOK_SECRET: z.string().optional(),

  ALGOLIA_APP_ID: z.string().optional(),
  ALGOLIA_ADMIN_KEY: z.string().optional(),

  /** Claude API для AI-ассиста контента (FR-A12); без ключа фича в режиме «отложено». */
  ANTHROPIC_API_KEY: z.string().optional(),

  CRON_SECRET: z.string().optional(),
  SMTP_URL: z.string().optional(),
});

export type Env = z.infer<typeof baseSchema>;

function load(): Env {
  const parsed = baseSchema.parse(process.env);

  if (parsed.NODE_ENV === "production") {
    // В проде эти секреты обязательны — никаких значений по умолчанию.
    const required: (keyof Env)[] = [
      "STRIPE_SECRET_KEY",
      "STRIPE_WEBHOOK_SECRET",
      "STRIPE_BILLING_WEBHOOK_SECRET",
      "CRON_SECRET",
    ];
    const missing = required.filter((k) => !parsed[k]);
    if (missing.length > 0) {
      throw new Error(`Отсутствуют обязательные секреты в production: ${missing.join(", ")}`);
    }
  }

  return parsed;
}

export const env = load();
export const isProd = env.NODE_ENV === "production";
