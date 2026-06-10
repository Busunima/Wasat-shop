package com.wasat.shop.feature.cart

import com.wasat.shop.core.db.CartItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CartMergeTest {

    private fun item(
        productId: String,
        qty: Int,
        variantKey: String = "",
        price: Long = 100,
        addedAt: Long = 0,
    ) = CartItemEntity(
        storeId = "s1",
        productId = productId,
        variantKey = variantKey,
        name = "Item $productId",
        price = price,
        currency = "USD",
        imageUrl = null,
        quantity = qty,
        addedAt = addedAt,
    )

    @Test
    fun `непересекающиеся позиции объединяются без изменений`() {
        val merged = CartMerge.merge(
            local = listOf(item("a", 1)),
            remote = listOf(item("b", 2)),
        )
        assertEquals(setOf("a", "b"), merged.map { it.productId }.toSet())
        assertEquals(3, CartTotals.itemCount(merged))
    }

    @Test
    fun `совпадающая позиция - количества суммируются`() {
        val merged = CartMerge.merge(
            local = listOf(item("a", 2)),
            remote = listOf(item("a", 3)),
        )
        assertEquals(1, merged.size)
        assertEquals(5, merged.single().quantity)
    }

    @Test
    fun `сумма количеств клампится до 99`() {
        val merged = CartMerge.merge(
            local = listOf(item("a", 60)),
            remote = listOf(item("a", 60)),
        )
        assertEquals(99, merged.single().quantity)
    }

    @Test
    fun `метаданные берутся из более свежей позиции`() {
        val merged = CartMerge.merge(
            local = listOf(item("a", 1, price = 150, addedAt = 2_000)),
            remote = listOf(item("a", 1, price = 100, addedAt = 1_000)),
        )
        assertEquals(150L, merged.single().price)
        assertEquals(2, merged.single().quantity)
    }

    @Test
    fun `разные варианты одного товара - разные позиции`() {
        val merged = CartMerge.merge(
            local = listOf(item("a", 1, variantKey = "size=M")),
            remote = listOf(item("a", 1, variantKey = "size=L")),
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `пустые корзины`() {
        assertEquals(emptyList<CartItemEntity>(), CartMerge.merge(emptyList(), emptyList()))
        assertEquals(1, CartMerge.merge(listOf(item("a", 1)), emptyList()).size)
        assertEquals(1, CartMerge.merge(emptyList(), listOf(item("a", 1))).size)
    }
}
