import { z } from "zod";

/**
 * Канонический enum статусов заказа — единый источник истины.
 * СИНХРОНИЗИРОВАН с docs/order-status.md и android OrderStatus.kt (ТЗ §FR-A04).
 * Любое изменение — сначала в docs/order-status.md, затем здесь и в Kotlin.
 */
export const ORDER_STATUS = [
  // happy path
  "NEW",
  "CONFIRMED",
  "PROCESSING",
  "SHIPPED",
  "DELIVERED",
  "COMPLETED",
  // терминальные
  "CANCELLED",
  "RETURN_REQUESTED",
  "RETURNED",
  "REFUNDED",
] as const;

export const orderStatusSchema = z.enum(ORDER_STATUS);
export type OrderStatus = z.infer<typeof orderStatusSchema>;

/** Статусы, в которых покупатель может отменить заказ (ТЗ §FR-B06). */
export const CANCELLABLE_BY_BUYER: readonly OrderStatus[] = ["NEW", "CONFIRMED", "PROCESSING"];

export function isCancellableByBuyer(status: OrderStatus): boolean {
  return CANCELLABLE_BY_BUYER.includes(status);
}
