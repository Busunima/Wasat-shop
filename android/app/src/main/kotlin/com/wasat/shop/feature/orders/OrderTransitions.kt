package com.wasat.shop.feature.orders

import com.wasat.shop.core.network.dto.CheckoutVariantDto
import com.wasat.shop.domain.model.OrderStatus

/**
 * Допустимые переходы статусов — зеркало server/src/schemas/order.ts
 * (ALLOWED_TRANSITIONS, источник истины docs/order-status.md). Pure JVM.
 */
object OrderTransitions {
    val allowed: Map<OrderStatus, List<OrderStatus>> = mapOf(
        OrderStatus.NEW to listOf(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
        OrderStatus.CONFIRMED to listOf(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
        OrderStatus.PROCESSING to listOf(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
        OrderStatus.SHIPPED to listOf(OrderStatus.DELIVERED),
        OrderStatus.DELIVERED to listOf(OrderStatus.COMPLETED, OrderStatus.RETURN_REQUESTED),
        OrderStatus.COMPLETED to listOf(OrderStatus.RETURN_REQUESTED),
        OrderStatus.CANCELLED to emptyList(),
        OrderStatus.RETURN_REQUESTED to listOf(OrderStatus.RETURNED, OrderStatus.COMPLETED),
        OrderStatus.RETURNED to listOf(OrderStatus.REFUNDED),
        OrderStatus.REFUNDED to emptyList(),
    )

    fun next(from: OrderStatus): List<OrderStatus> = allowed[from] ?: emptyList()

    /** Безопасный парс серверной строки статуса; null — неизвестный статус. */
    fun parse(raw: String): OrderStatus? = OrderStatus.entries.firstOrNull { it.name == raw }
}

/**
 * Ключ варианта корзины ("size=M;color=red", см. CartTotals.variantKey) →
 * селектор для чекаута. "" → null (товар без вариантов). Pure JVM.
 */
object VariantKeyParser {
    fun parse(key: String): CheckoutVariantDto? {
        if (key.isBlank()) return null
        val parts = key.split(";").mapNotNull { part ->
            val (k, v) = part.split("=", limit = 2).let { it.getOrNull(0) to it.getOrNull(1) }
            if (k.isNullOrBlank() || v.isNullOrBlank()) null else k to v
        }.toMap()
        if (parts.isEmpty()) return null
        return CheckoutVariantDto(size = parts["size"], color = parts["color"])
    }
}
