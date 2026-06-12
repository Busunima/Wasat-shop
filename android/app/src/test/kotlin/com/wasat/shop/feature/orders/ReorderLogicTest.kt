package com.wasat.shop.feature.orders

import com.wasat.shop.core.network.dto.CheckoutVariantDto
import com.wasat.shop.domain.model.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderLogicTest {

    @Test
    fun `isReorderable - завершённые и отменённые, но не активные`() {
        assertTrue(ReorderLogic.isReorderable(OrderStatus.DELIVERED))
        assertTrue(ReorderLogic.isReorderable(OrderStatus.COMPLETED))
        assertTrue(ReorderLogic.isReorderable(OrderStatus.CANCELLED))
        assertTrue(ReorderLogic.isReorderable(OrderStatus.REFUNDED))
        assertFalse(ReorderLogic.isReorderable(OrderStatus.NEW))
        assertFalse(ReorderLogic.isReorderable(OrderStatus.SHIPPED))
        assertFalse(ReorderLogic.isReorderable(null))
    }

    @Test
    fun `variantKeyOf - зеркало CartTotals_variantKey`() {
        assertEquals("", ReorderLogic.variantKeyOf(null))
        assertEquals("size=M", ReorderLogic.variantKeyOf(CheckoutVariantDto(size = "M")))
        assertEquals(
            "size=M;color=red",
            ReorderLogic.variantKeyOf(CheckoutVariantDto(size = "M", color = "red")),
        )
        assertEquals("color=red", ReorderLogic.variantKeyOf(CheckoutVariantDto(color = "red")))
    }

    @Test
    fun `variantKeyOf - round-trip через VariantKeyParser`() {
        val variant = CheckoutVariantDto(size = "L", color = "blue")
        val parsed = VariantKeyParser.parse(ReorderLogic.variantKeyOf(variant))
        assertEquals(variant.size, parsed?.size)
        assertEquals(variant.color, parsed?.color)
    }
}
