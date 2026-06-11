package com.wasat.shop.feature.orders

import com.wasat.shop.domain.model.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderTransitionsTest {

    @Test
    fun `next - happy path и терминальные`() {
        assertTrue(OrderTransitions.next(OrderStatus.NEW).contains(OrderStatus.CONFIRMED))
        assertTrue(OrderTransitions.next(OrderStatus.NEW).contains(OrderStatus.CANCELLED))
        assertTrue(OrderTransitions.next(OrderStatus.SHIPPED).contains(OrderStatus.DELIVERED))
        assertTrue(OrderTransitions.next(OrderStatus.CANCELLED).isEmpty())
        assertTrue(OrderTransitions.next(OrderStatus.REFUNDED).isEmpty())
    }

    @Test
    fun `allowed - покрывает все статусы enum (зеркало сервера)`() {
        OrderStatus.entries.forEach { status ->
            assertTrue("нет переходов для $status", OrderTransitions.allowed.containsKey(status))
        }
    }

    @Test
    fun `parse - известный и неизвестный статус`() {
        assertEquals(OrderStatus.SHIPPED, OrderTransitions.parse("SHIPPED"))
        assertNull(OrderTransitions.parse("GHOST"))
    }
}

class VariantKeyParserTest {

    @Test
    fun `пустой ключ - null (товар без вариантов)`() {
        assertNull(VariantKeyParser.parse(""))
        assertNull(VariantKeyParser.parse("   "))
    }

    @Test
    fun `size и color из ключа корзины`() {
        val v = VariantKeyParser.parse("size=M;color=red")
        assertEquals("M", v?.size)
        assertEquals("red", v?.color)
    }

    @Test
    fun `только size`() {
        val v = VariantKeyParser.parse("size=L")
        assertEquals("L", v?.size)
        assertNull(v?.color)
    }

    @Test
    fun `мусорный ключ без пар - null`() {
        assertNull(VariantKeyParser.parse("garbage"))
    }
}
