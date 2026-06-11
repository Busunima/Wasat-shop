import { z } from "zod";

/**
 * Сотрудники магазина (ТЗ §6 FR-A09). Владелец приглашает зарегистрированных
 * пользователей и назначает роль. Роли (кроме owner) задают доступ к управлению
 * каталогом/остатками/заказами; настройки, биллинг, промокоды и сам список
 * сотрудников остаются за владельцем.
 */

/** Назначаемые владельцем роли (owner не назначается — он создатель магазина). */
export const STAFF_ROLES = ["manager", "staff"] as const;
export type StaffRole = (typeof STAFF_ROLES)[number];

/** Все роли членства в магазине (для проверки доступа в middleware). */
export const STORE_ROLES = ["owner", ...STAFF_ROLES] as const;

export const staffInviteSchema = z.object({
  email: z.string().email().max(254),
  role: z.enum(STAFF_ROLES).default("staff"),
});
export type StaffInvite = z.infer<typeof staffInviteSchema>;

export const staffRoleUpdateSchema = z.object({
  role: z.enum(STAFF_ROLES),
});
export type StaffRoleUpdate = z.infer<typeof staffRoleUpdateSchema>;
