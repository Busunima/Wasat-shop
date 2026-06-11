import assert from "node:assert/strict";
import { test } from "node:test";
import { aiDescribeSchema } from "../../src/schemas/ai.ts";
import { buildDescriptionPrompt, isAiConfigured } from "../../src/services/ai.ts";

test("aiDescribeSchema: дефолты и границы", () => {
  const ok = aiDescribeSchema.parse({ name: "Кеды" });
  assert.equal(ok.language, "ru");
  assert.deepEqual(ok.tags, []);
  assert.throws(() => aiDescribeSchema.parse({ name: "" }));
  assert.throws(() => aiDescribeSchema.parse({ name: "x".repeat(201) }));
  assert.throws(() => aiDescribeSchema.parse({ name: "ok", language: "fr" }));
});

test("buildDescriptionPrompt: включает все поля и язык", () => {
  const prompt = buildDescriptionPrompt(
    aiDescribeSchema.parse({
      name: "Кеды Air",
      category: "Обувь",
      tags: ["лето", "красные"],
      hints: "натуральная замша",
    }),
    "Кеды и Ко",
  );
  assert.ok(prompt.includes("Кеды и Ко"));
  assert.ok(prompt.includes("Кеды Air"));
  assert.ok(prompt.includes("Обувь"));
  assert.ok(prompt.includes("лето, красные"));
  assert.ok(prompt.includes("натуральная замша"));
  assert.ok(prompt.includes("ТОЛЬКО текстом описания"));
});

test("buildDescriptionPrompt: английский вариант без лишних строк", () => {
  const prompt = buildDescriptionPrompt(
    aiDescribeSchema.parse({ name: "Sneakers", language: "en" }),
    "Shoe Store",
  );
  assert.ok(prompt.includes("ONLY the description text"));
  assert.ok(!prompt.includes("Категория:")); // категория не задана
  assert.ok(!prompt.includes("Подсказки"));
});

test("isAiConfigured: в тестовой среде ключа нет", () => {
  assert.equal(isAiConfigured(), false);
});
