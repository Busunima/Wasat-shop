import { z } from "zod";

/**
 * Схемы товара (docs/data-model.md → products/{pid}, FR-A02).
 * Цены — в минорных единицах валюты магазина (int). totalStock/rating/reviewCount —
 * производные, клиент их не присылает (пишет только сервер).
 */

export const PRODUCT_STATUSES = ["active", "draft", "archived"] as const;

export const productVariantSchema = z.object({
  size: z.string().max(40).optional(),
  color: z.string().max(40).optional(),
  stock: z.number().int().min(0),
  sku: z.string().max(64).optional(),
});

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

export const productCreateSchema = z.object({
  name: z.string().min(1).max(200),
  description: z.string().max(5000).optional(),
  price: z.number().int().min(0),
  originalPrice: z.number().int().min(0).nullable().optional(),
  images: z.array(z.string().url()).max(10).default([]),
  category: optionalTrimmed(80),
  tags: z.array(z.string().min(1).max(40)).max(20).default([]),
  variants: z.array(productVariantSchema).max(100).default([]),
  status: z.enum(PRODUCT_STATUSES).default("draft"),
  // SKU и штрихкод уровня товара (FR-A02); у вариантов — собственный sku
  sku: optionalTrimmed(64),
  barcode: optionalTrimmed(64),
});

export const productUpdateSchema = productCreateSchema.partial();

export type ProductVariant = z.infer<typeof productVariantSchema>;
export type ProductCreate = z.infer<typeof productCreateSchema>;
export type ProductUpdate = z.infer<typeof productUpdateSchema>;

/** totalStock — производное: сумма stock по вариантам; без вариантов — 0. */
export function computeTotalStock(variants: ProductVariant[]): number {
  return variants.reduce((sum, v) => sum + v.stock, 0);
}

// ── Листинг каталога (FR-B02): фильтры, сортировка, курсорная пагинация ──────

export const PRODUCT_SORTS = ["new", "price_asc", "price_desc", "rating"] as const;
export type ProductSort = (typeof PRODUCT_SORTS)[number];

/** Query-параметры GET /stores/:id/products (всё опционально, строки из URL). */
export const productListQuerySchema = z.object({
  category: z.string().max(80).optional(),
  /** Поиск по названию (временно Firestore/in-memory до Algolia, ТЗ §2). */
  q: z.string().max(120).optional(),
  inStock: z
    .enum(["true", "false"])
    .optional()
    .transform((v) => v === "true"),
  minPrice: z.coerce.number().int().min(0).optional(),
  maxPrice: z.coerce.number().int().min(0).optional(),
  sort: z.enum(PRODUCT_SORTS).default("new"),
  limit: z.coerce.number().int().min(1).max(50).default(20),
  cursor: z.string().optional(),
});

export type ProductListQuery = z.infer<typeof productListQuerySchema>;

/** Курсор пагинации: значение поля сортировки последнего элемента + его id. */
export interface PageCursor {
  v: number; // значение сортировки (price/rating/createdAt в ms)
  id: string;
}

export function encodeCursor(cursor: PageCursor): string {
  return Buffer.from(JSON.stringify(cursor)).toString("base64url");
}

/** null — битый/чужой курсор (клиент начнёт с первой страницы). */
export function decodeCursor(raw: string): PageCursor | null {
  try {
    const parsed = JSON.parse(Buffer.from(raw, "base64url").toString("utf8")) as PageCursor;
    if (typeof parsed.v !== "number" || typeof parsed.id !== "string") return null;
    return parsed;
  } catch {
    return null;
  }
}
