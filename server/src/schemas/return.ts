import { z } from "zod";

/**
 * Возвраты (ТЗ §6 FR-B09/A11). Жизненный цикл отдельным enum, связан с заказом
 * (docs/order-status.md). Покупатель инициирует по доставленному заказу; владелец
 * рассматривает, принимает товар (ресток) и оформляет возмещение. Реальный Stripe
 * Refund — env-gated (deferred), как и приём оплаты в чекауте.
 */
export const RETURN_STATUS = ["REQUESTED", "APPROVED", "REJECTED", "RECEIVED", "REFUNDED"] as const;
export const returnStatusSchema = z.enum(RETURN_STATUS);
export type ReturnStatus = z.infer<typeof returnStatusSchema>;

export const returnItemSchema = z.object({
  productId: z.string().min(1).max(64),
  qty: z.number().int().min(1).max(99),
});

/** Заявка покупателя: позиции (подмножество заказа) + причина. */
export const returnCreateSchema = z.object({
  orderId: z.string().min(1).max(64),
  items: z.array(returnItemSchema).min(1).max(50),
  reason: z.string().min(3).max(1000),
});
export type ReturnCreate = z.infer<typeof returnCreateSchema>;

/** Решение владельца по заявке (REQUESTED → APPROVED/REJECTED). */
export const returnResolveSchema = z.object({
  action: z.enum(["approve", "reject"]),
  comment: z.string().max(1000).optional(),
});
export type ReturnResolve = z.infer<typeof returnResolveSchema>;

export const returnsListQuerySchema = z.object({
  status: returnStatusSchema.optional(),
  limit: z.coerce.number().int().min(1).max(100).default(50),
});

/**
 * Допустимые переходы статуса возврата (pure — под unit-тестом).
 * REQUESTED → APPROVED|REJECTED; APPROVED → RECEIVED (ресток); RECEIVED → REFUNDED.
 */
export const RETURN_TRANSITIONS: Record<ReturnStatus, readonly ReturnStatus[]> = {
  REQUESTED: ["APPROVED", "REJECTED"],
  APPROVED: ["RECEIVED"],
  REJECTED: [],
  RECEIVED: ["REFUNDED"],
  REFUNDED: [],
};

export function canTransitionReturn(from: ReturnStatus, to: ReturnStatus): boolean {
  return RETURN_TRANSITIONS[from].includes(to);
}

/** Сумма возмещения по возвращаемым позициям (минорные единицы). Pure. */
export function computeRefundAmount(
  orderItems: Array<{ productId: string; price: number; qty: number }>,
  returnItems: Array<{ productId: string; qty: number }>,
): number {
  let total = 0;
  for (const ret of returnItems) {
    const orderItem = orderItems.find((it) => it.productId === ret.productId);
    if (!orderItem) continue;
    const qty = Math.min(ret.qty, orderItem.qty);
    total += orderItem.price * qty;
  }
  return total;
}
