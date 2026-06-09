package com.wasat.shop.domain.model

/**
 * Канонический enum статусов заказа — единый источник истины.
 * СИНХРОНИЗИРОВАН с docs/order-status.md и server schemas/orderStatus.ts (ТЗ §FR-A04).
 * Любое изменение — сначала в docs/order-status.md, затем здесь и в Zod-схеме.
 */
enum class OrderStatus {
    // happy path
    NEW,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    COMPLETED,

    // терминальные
    CANCELLED,
    RETURN_REQUESTED,
    RETURNED,
    REFUNDED;

    /** Может ли покупатель отменить заказ в этом статусе (ТЗ §FR-B06). */
    val isCancellableByBuyer: Boolean
        get() = this == NEW || this == CONFIRMED || this == PROCESSING
}
