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

export const productCreateSchema = z.object({
  name: z.string().min(1).max(200),
  description: z.string().max(5000).optional(),
  price: z.number().int().min(0),
  originalPrice: z.number().int().min(0).optional(),
  images: z.array(z.string().url()).max(10).default([]),
  category: z.string().max(80).optional(),
  tags: z.array(z.string().min(1).max(40)).max(20).default([]),
  variants: z.array(productVariantSchema).max(100).default([]),
  status: z.enum(PRODUCT_STATUSES).default("draft"),
});

export const productUpdateSchema = productCreateSchema.partial();

export type ProductVariant = z.infer<typeof productVariantSchema>;
export type ProductCreate = z.infer<typeof productCreateSchema>;
export type ProductUpdate = z.infer<typeof productUpdateSchema>;

/** totalStock — производное: сумма stock по вариантам; без вариантов — 0. */
export function computeTotalStock(variants: ProductVariant[]): number {
  return variants.reduce((sum, v) => sum + v.stock, 0);
}
