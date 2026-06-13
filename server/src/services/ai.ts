import Anthropic from "@anthropic-ai/sdk";
import { env } from "../config/env.js";
import { ApiError } from "../middleware/errorHandler.js";
import type { AiDescribe } from "../schemas/ai.js";
import { logger } from "../lib/logger.js";

/**
 * AI-ассист контента (ТЗ §6 FR-A12): генерация описания товара через Claude API.
 * Env-gated как Stripe: без ANTHROPIC_API_KEY — NOT_IMPLEMENTED (клиент показывает
 * «AI-ассист не настроен»). Официальный SDK @anthropic-ai/sdk; ретраи 429/5xx —
 * встроенные в SDK.
 */

const MODEL = "claude-opus-4-8";
const MAX_TOKENS = 1024; // описание товара — намеренно короткий вывод

let client: Anthropic | undefined;

function anthropic(): Anthropic {
  if (!env.ANTHROPIC_API_KEY) {
    throw new ApiError("NOT_IMPLEMENTED", "AI-ассист не настроен (нет ANTHROPIC_API_KEY)");
  }
  if (!client) client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY });
  return client;
}

/** Доступность фичи — для смоков/диагностики. */
export function isAiConfigured(): boolean {
  return Boolean(env.ANTHROPIC_API_KEY);
}

/**
 * Промпт генерации описания (pure — под unit-тестом). Просим только текст
 * описания без преамбул; границы длины зеркалят схему товара (description ≤5000).
 */
export function buildDescriptionPrompt(input: AiDescribe, storeName: string): string {
  const rewrite = input.mode === "rewrite" && Boolean(input.current?.trim());
  const lines = [
    rewrite
      ? input.language === "ru"
        ? `Перепиши и улучши описание товара для интернет-магазина «${storeName}», ` +
            "сохранив факты и характеристики из исходного текста."
        : `Rewrite and improve the product description for the online store ` +
            `"${storeName}", preserving the facts and specs from the original.`
      : input.language === "ru"
        ? `Напиши продающее описание товара для интернет-магазина «${storeName}».`
        : `Write a compelling product description for the online store "${storeName}".`,
    `Товар: ${input.name}`,
  ];
  if (input.category) lines.push(`Категория: ${input.category}`);
  if (input.tags.length > 0) lines.push(`Теги: ${input.tags.join(", ")}`);
  if (rewrite) lines.push(`Исходное описание: ${input.current!.trim()}`);
  if (input.hints) lines.push(`Подсказки продавца: ${input.hints}`);
  lines.push(
    input.language === "ru"
      ? "Требования: 2–4 коротких абзаца, без заголовков и списков, без преамбулы " +
          "(«Вот описание…») и без выдуманных характеристик — опирайся только на " +
          "данные выше. Ответь ТОЛЬКО текстом описания."
      : "Requirements: 2–4 short paragraphs, no headings or lists, no preamble " +
          "and no invented specs — rely only on the data above. " +
          "Reply with ONLY the description text.",
  );
  return lines.join("\n");
}

/** Генерация описания товара (FR-A12). */
export async function generateDescription(
  input: AiDescribe,
  storeName: string,
): Promise<{ description: string }> {
  let response: Anthropic.Message;
  try {
    response = await anthropic().messages.create({
      model: MODEL,
      max_tokens: MAX_TOKENS,
      messages: [{ role: "user", content: buildDescriptionPrompt(input, storeName) }],
    });
  } catch (err) {
    // Читаемая причина для клиента (нет кредитов / невалидный ключ / перегрузка)
    if (err instanceof Anthropic.APIError) {
      const code = err.status === 429 ? "RATE_LIMITED" : "INTERNAL";
      throw new ApiError(code, `Claude API: ${err.message}`);
    }
    throw err;
  }

  const description = response.content
    .filter((block) => block.type === "text")
    .map((block) => block.text)
    .join("")
    .trim();

  if (!description) {
    throw new ApiError("INTERNAL", "AI не вернул текст описания");
  }

  logger.info("AI-описание сгенерировано", {
    model: MODEL,
    outputTokens: response.usage.output_tokens,
  });
  return { description: description.slice(0, 5000) };
}
