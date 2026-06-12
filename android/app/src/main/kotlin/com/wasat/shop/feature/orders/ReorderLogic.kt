package com.wasat.shop.feature.orders

import com.wasat.shop.core.network.dto.CheckoutVariantDto
import com.wasat.shop.domain.model.OrderStatus

/** Повторный заказ (ТЗ §6 FR-B11). Pure JVM — под unit-тестом. */
object ReorderLogic {
    /** Завершённые/отменённые заказы — кандидаты на «Повторить заказ». */
    private val REORDERABLE = setOf(
        OrderStatus.DELIVERED,
        OrderStatus.COMPLETED,
        OrderStatus.CANCELLED,
        OrderStatus.RETURNED,
        OrderStatus.REFUNDED,
    )

    fun isReorderable(status: OrderStatus?): Boolean = status in REORDERABLE

    /** Ключ варианта позиции заказа — зеркало CartTotals.variantKey ("size=M;color=red"). */
    fun variantKeyOf(variant: CheckoutVariantDto?): String {
        if (variant == null) return ""
        return listOfNotNull(
            variant.size?.let { "size=$it" },
            variant.color?.let { "color=$it" },
        ).joinToString(";")
    }
}
