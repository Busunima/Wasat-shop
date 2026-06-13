import { z } from "zod";

/**
 * Категории магазина (FR-A01, docs/data-model.md → categories/{cid}).
 * Дерево через parentId (1–2 уровня в MVP); slug — латиница для ЧПУ/фильтра.
 */

export const CATEGORY_SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

const slugField = z
  .string()
  .trim()
  .min(2)
  .max(40)
  .regex(CATEGORY_SLUG, "строчные латиница, цифры и дефис");

/** URL картинки: "" / null → null (очистка); отсутствие → не трогать. */
const optionalImageUrl = z
  .string()
  .url()
  .or(z.literal(""))
  .nullish()
  .transform((v) => (v === undefined ? undefined : v ? v : null));

/** "" / null → null (корень); отсутствие → не трогать (только в PATCH). */
const optionalParentId = z
  .string()
  .max(64)
  .nullish()
  .transform((v) => (v ? v : null));

export const categoryCreateSchema = z.object({
  name: z.string().trim().min(1).max(80),
  slug: slugField,
  parentId: optionalParentId,
  order: z.number().int().min(0).max(100000).default(0),
  imageUrl: optionalImageUrl,
});
export type CategoryCreate = z.infer<typeof categoryCreateSchema>;

export const categoryUpdateSchema = z.object({
  name: z.string().trim().min(1).max(80).optional(),
  slug: slugField.optional(),
  /** "" / null — перенести в корень; отсутствие — не трогать. */
  parentId: z
    .string()
    .max(64)
    .nullable()
    .optional()
    .transform((v) => (v === undefined ? undefined : v ? v : null)),
  order: z.number().int().min(0).max(100000).optional(),
  imageUrl: optionalImageUrl,
});
export type CategoryUpdate = z.infer<typeof categoryUpdateSchema>;
