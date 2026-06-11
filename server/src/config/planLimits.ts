import type { Plan } from "../schemas/store.js";

/**
 * Лимиты тарифов (ТЗ §8, §10.2, FR-S03). null — без ограничения (enterprise).
 * Энфорсятся при создании товаров и приглашении сотрудников; суперадмин меняет
 * тариф магазина (FR-S02). Значения — чистые данные, под unit-тестами.
 */

export interface PlanLimits {
  /** Максимум активных + черновиков товаров; null — без лимита. */
  maxProducts: number | null;
  /** Максимум сотрудников (без учёта владельца); null — без лимита. */
  maxStaff: number | null;
}

export const PLAN_LIMITS: Record<Plan, PlanLimits> = {
  free: { maxProducts: 25, maxStaff: 0 },
  basic: { maxProducts: 250, maxStaff: 3 },
  pro: { maxProducts: 2000, maxStaff: 15 },
  enterprise: { maxProducts: null, maxStaff: null },
};

/** Лимиты тарифа; неизвестный тариф трактуется как free (безопасный минимум). */
export function limitsFor(plan: string): PlanLimits {
  return PLAN_LIMITS[plan as Plan] ?? PLAN_LIMITS.free;
}

/**
 * Можно ли добавить ещё одну сущность при текущем количестве. false — лимит исчерпан.
 * null-лимит (enterprise) всегда разрешает.
 */
export function canAdd(limit: number | null, currentCount: number): boolean {
  return limit === null || currentCount < limit;
}
