import { z } from "zod";
import { PLANS } from "./store.js";

/**
 * Схемы суперадмин-эндпоинтов (ТЗ §7 FR-S01/S02). Доступ — Custom Claim
 * superadmin: true (middleware requireSuperadmin).
 */

/** GET /api/admin/stores — поиск/пагинация списка магазинов. */
export const adminStoreListQuerySchema = z.object({
  /** Поиск по storeId / slug / ownerEmail / name (подстрока, регистронезависимо). */
  q: z.string().max(120).optional(),
  /** Фильтр по тарифу. */
  plan: z.enum(PLANS).optional(),
  /** Фильтр по блокировке. */
  blocked: z
    .enum(["true", "false"])
    .optional()
    .transform((v) => (v === undefined ? undefined : v === "true")),
  limit: z.coerce.number().int().min(1).max(100).default(50),
  cursor: z.string().optional(),
});
export type AdminStoreListQuery = z.infer<typeof adminStoreListQuerySchema>;

/** PATCH /api/admin/stores/:id/block — блокировка/разблокировка с причиной. */
export const adminBlockSchema = z.object({
  blocked: z.boolean(),
  reason: z.string().max(500).optional(),
});
export type AdminBlock = z.infer<typeof adminBlockSchema>;

/** PATCH /api/admin/stores/:id/plan — смена тарифа. */
export const adminPlanSchema = z.object({
  plan: z.enum(PLANS),
});
export type AdminPlan = z.infer<typeof adminPlanSchema>;
