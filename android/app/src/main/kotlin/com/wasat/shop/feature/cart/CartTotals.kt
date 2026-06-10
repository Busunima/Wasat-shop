package com.wasat.shop.feature.cart

import com.wasat.shop.core.db.CartItemEntity
import com.wasat.shop.core.network.dto.VariantDto

/** Чистая логика корзины (pure JVM — покрыта unit-тестами). */
object CartTotals {
    const val MAX_QTY_PER_ITEM = 99

    /** Сумма корзины в минорных единицах. Налоги/доставка — на чекауте (Фаза 4). */
    fun subtotal(items: List<CartItemEntity>): Long =
        items.sumOf { it.price * it.quantity }

    fun itemCount(items: List<CartItemEntity>): Int = items.sumOf { it.quantity }

    fun clampQuantity(qty: Int): Int = qty.coerceIn(1, MAX_QTY_PER_ITEM)

    /**
     * Нормализованный ключ варианта: стабилен независимо от порядка полей,
     * "" — товар без вариантов. Формат: "size=M;color=red".
     */
    fun variantKey(variant: VariantDto?): String {
        if (variant == null) return ""
        return listOfNotNull(
            variant.size?.let { "size=$it" },
            variant.color?.let { "color=$it" },
        ).joinToString(";")
    }

    /** Человекочитаемая подпись варианта из ключа: "size=M;color=red" → "M · red". */
    fun variantLabel(variantKey: String): String =
        variantKey.split(";")
            .mapNotNull { part -> part.substringAfter("=", "").takeIf { it.isNotEmpty() } }
            .joinToString(" · ")
}
