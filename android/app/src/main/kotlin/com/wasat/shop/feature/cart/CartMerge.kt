package com.wasat.shop.feature.cart

import com.wasat.shop.core.db.CartItemEntity

/**
 * Правило слияния гостевой и серверной корзины при входе (ТЗ FR-B04).
 * Ключ позиции — productId+variantKey (в рамках одного магазина):
 *  - позиция есть в обеих — количества СУММИРУЮТСЯ (клампинг 1..99),
 *    цена/метаданные берутся из более свежей (addedAt);
 *  - только в одной — переносится как есть.
 * Pure JVM — под unit-тестами.
 */
object CartMerge {
    fun merge(local: List<CartItemEntity>, remote: List<CartItemEntity>): List<CartItemEntity> {
        val byKey = LinkedHashMap<String, CartItemEntity>()
        for (item in remote) byKey[key(item)] = item
        for (item in local) {
            val existing = byKey[key(item)]
            byKey[key(item)] = if (existing == null) {
                item
            } else {
                val newer = if (item.addedAt >= existing.addedAt) item else existing
                newer.copy(
                    quantity = CartTotals.clampQuantity(item.quantity + existing.quantity),
                )
            }
        }
        return byKey.values.toList()
    }

    private fun key(item: CartItemEntity): String = "${item.productId}|${item.variantKey}"
}
