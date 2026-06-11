import { z } from "zod";
import { ORDER_STATUS, type OrderStatus } from "./orderStatus.js";

/**
 * Заказы (ТЗ §FR-B05/A04/B06). Чекаут — POST /api/checkout (§10.1): сервер сам
 * считает цены/суммы (клиентским не доверяет, §13), атомарно списывает сток и
 * создаёт заказ. Оплата в MVP-ядре deferred (payment.method="deferred") —
 * Stripe PaymentSheet подключается к готовому ядру при появлении ключей.
 */

export const DELIVERY_METHODS = ["pickup", "courier"] as const;

const variantSelectorSchema = z
  .object({
    sku: z.string().max(64).optional(),
    size: z.string().max(40).optional(),
    color: z.string().max(40).optional(),
  })
  .optional();

export const checkoutItemSchema = z.object({
  productId: z.string().min(1).max(64),
  qty: z.number().int().min(1).max(99),
  variant: variantSelectorSchema,
});

export const checkoutSchema = z
  .object({
    storeId: z.string().min(1).max(64),
    items: z.array(checkoutItemSchema).min(1).max(50),
    promoCode: z
      .string()
      .max(32)
      .optional()
      .transform((v) => v?.trim().toUpperCase() || undefined),
    delivery: z.object({
      method: z.enum(DELIVERY_METHODS),
      address: z.string().max(300).optional(),
    }),
    /** Ключ идемпотентности (§10.1): повтор с тем же ключом вернёт тот же заказ. */
    idempotencyKey: z.string().min(8).max(64),
    customerEmail: z.string().email().max(254).optional(),
  })
  .refine((c) => c.delivery.method !== "courier" || Boolean(c.delivery.address?.trim()), {
    message: "Для курьерской доставки нужен адрес",
    path: ["delivery", "address"],
  });
export type CheckoutInput = z.infer<typeof checkoutSchema>;

/** Смена статуса владельцем/сотрудником (FR-A04). trackingNo — для SHIPPED. */
export const orderStatusUpdateSchema = z.object({
  status: z.enum(ORDER_STATUS),
  trackingNo: z.string().max(64).optional(),
});

export const ordersListQuerySchema = z.object({
  status: z.enum(ORDER_STATUS).optional(),
  limit: z.coerce.number().int().min(1).max(100).default(50),
});

// ── Чистая логика (под unit-тестами) ─────────────────────────────────────────

/**
 * Допустимые переходы статусов (docs/order-status.md). Возвраты (RETURN_*)
 * управляются отдельным флоу FR-A11 (returns/{rid}) — здесь только заявка
 * покупателя после доставки.
 */
export const ALLOWED_TRANSITIONS: Record<OrderStatus, readonly OrderStatus[]> = {
  NEW: ["CONFIRMED", "CANCELLED"],
  CONFIRMED: ["PROCESSING", "CANCELLED"],
  PROCESSING: ["SHIPPED", "CANCELLED"],
  SHIPPED: ["DELIVERED"],
  DELIVERED: ["COMPLETED", "RETURN_REQUESTED"],
  COMPLETED: ["RETURN_REQUESTED"],
  CANCELLED: [],
  RETURN_REQUESTED: ["RETURNED", "COMPLETED"],
  RETURNED: ["REFUNDED"],
  REFUNDED: [],
};

export function canTransition(from: OrderStatus, to: OrderStatus): boolean {
  return ALLOWED_TRANSITIONS[from].includes(to);
}

/** Статусы, в которых отмена восстанавливает сток (оплата deferred — без рефанда). */
export const RESTOCK_ON_CANCEL: readonly OrderStatus[] = ["NEW", "CONFIRMED", "PROCESSING"];

export interface TotalsInput {
  subtotal: number;
  discount: number;
  freeShipping: boolean;
  method: (typeof DELIVERY_METHODS)[number];
  /** Стоимость курьерской доставки магазина (минорные), null — не задана. */
  storeDeliveryCost: number | null;
}

/** Разложение суммы заказа: total = subtotal − discount + delivery (tax — Stripe Tax позже). */
export function computeTotals(input: TotalsInput): { delivery: number; total: number } {
  const delivery =
    input.method === "courier" && !input.freeShipping ? (input.storeDeliveryCost ?? 0) : 0;
  const total = Math.max(0, input.subtotal - input.discount) + delivery;
  return { delivery, total };
}
