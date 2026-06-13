import { z } from "zod";

/**
 * Схемы инвентаря (ТЗ §6 FR-A03): корректировки стока и CSV-импорт.
 * Изменение остатка — ТОЛЬКО атомарной транзакцией сервера с записью в inventoryLog.
 */

/** Адресация варианта: по sku ЛИБО по паре size/color (как в variants товара). */
export const variantSelectorSchema = z
  .object({
    sku: z.string().max(64).optional(),
    size: z.string().max(40).optional(),
    color: z.string().max(40).optional(),
  })
  .refine((v) => v.sku !== undefined || v.size !== undefined || v.color !== undefined, {
    message: "укажите sku или size/color варианта",
  });

/** POST /stores/:id/products/:pid/stock — относительная корректировка остатка. */
export const stockAdjustSchema = z.object({
  /**
   * Для товара без вариантов variant опускается (корректируется totalStock);
   * для вариантного товара — обязателен.
   */
  variant: variantSelectorSchema.optional(),
  /** Дельта (может быть отрицательной); итог не может уйти ниже 0. */
  delta: z.number().int().min(-100_000).max(100_000),
  reason: z.string().max(200).default("manual"),
  /**
   * Ключ идемпотентности (offline-first): повтор с тем же ключом не применяет
   * дельту повторно. Нужен для безопасной доставки из клиентской outbox-очереди.
   */
  idempotencyKey: z.string().min(8).max(64).optional(),
});
export type StockAdjust = z.infer<typeof stockAdjustSchema>;

/** Строка CSV-импорта: sku;stock (абсолютное значение остатка). */
export const importRowSchema = z.object({
  sku: z.string().min(1).max(64),
  stock: z.coerce.number().int().min(0),
});

export interface ImportRowError {
  line: number;
  raw: string;
  message: string;
}

export interface ParsedImport {
  rows: Array<{ sku: string; stock: number; line: number }>;
  errors: ImportRowError[];
}

/**
 * Парсер CSV «sku,stock» (разделитель `,` или `;`, заголовок опционален).
 * Pure — под unit-тестами. Невалидные строки попадают в отчёт об ошибках (ТЗ FR-A03).
 */
export function parseInventoryCsv(text: string): ParsedImport {
  const rows: ParsedImport["rows"] = [];
  const errors: ImportRowError[] = [];

  const lines = text.split(/\r?\n/);
  lines.forEach((rawLine, index) => {
    const line = index + 1;
    const trimmed = rawLine.trim();
    if (!trimmed) return;
    // заголовок (sku,stock) пропускаем
    if (index === 0 && /^sku[,;]/i.test(trimmed)) return;

    const parts = trimmed.split(/[,;]/).map((p) => p.trim());
    if (parts.length !== 2) {
      errors.push({ line, raw: trimmed, message: "ожидаются 2 колонки: sku,stock" });
      return;
    }
    const parsed = importRowSchema.safeParse({ sku: parts[0], stock: parts[1] });
    if (!parsed.success) {
      errors.push({
        line,
        raw: trimmed,
        message: parsed.error.issues.map((i) => i.message).join("; "),
      });
      return;
    }
    rows.push({ ...parsed.data, line });
  });

  return { rows, errors };
}
