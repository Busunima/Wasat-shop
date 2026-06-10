package com.wasat.shop.feature.cart

import com.wasat.shop.core.db.CartItemEntity
import com.wasat.shop.core.network.dto.VariantDto
import org.junit.Assert.assertEquals
import org.junit.Test

class CartTotalsTest {

    private fun item(price: Long, qty: Int, variantKey: String = "") = CartItemEntity(
        storeId = "s1",
        productId = "p-$price-$qty-$variantKey",
        variantKey = variantKey,
        name = "Item",
        price = price,
        currency = "USD",
        imageUrl = null,
        quantity = qty,
        addedAt = 0,
    )

    @Test
    fun `subtotal — сумма цена x количество, пустая корзина = 0`() {
        assertEquals(0L, CartTotals.subtotal(emptyList()))
        assertEquals(
            2 * 1_990L + 3 * 500L,
            CartTotals.subtotal(listOf(item(1_990, 2), item(500, 3))),
        )
    }

    @Test
    fun `itemCount — сумма количеств`() {
        assertEquals(5, CartTotals.itemCount(listOf(item(1, 2), item(2, 3))))
    }

    @Test
    fun `clampQuantity держит количество в пределах 1-99`() {
        assertEquals(1, CartTotals.clampQuantity(0))
        assertEquals(1, CartTotals.clampQuantity(-5))
        assertEquals(42, CartTotals.clampQuantity(42))
        assertEquals(99, CartTotals.clampQuantity(150))
    }

    @Test
    fun `variantKey — нормализованный и стабильный`() {
        assertEquals("", CartTotals.variantKey(null))
        assertEquals("size=M", CartTotals.variantKey(VariantDto(size = "M", stock = 1)))
        assertEquals(
            "size=M;color=red",
            CartTotals.variantKey(VariantDto(size = "M", color = "red", stock = 1)),
        )
        assertEquals("color=red", CartTotals.variantKey(VariantDto(color = "red", stock = 1)))
    }

    @Test
    fun `variantLabel — человекочитаемая подпись из ключа`() {
        assertEquals("M · red", CartTotals.variantLabel("size=M;color=red"))
        assertEquals("M", CartTotals.variantLabel("size=M"))
        assertEquals("", CartTotals.variantLabel(""))
    }
}
