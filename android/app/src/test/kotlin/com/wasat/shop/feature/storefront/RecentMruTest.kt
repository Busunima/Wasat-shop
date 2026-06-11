package com.wasat.shop.feature.storefront

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentMruTest {

    private fun item(id: String) = RecentProduct(productId = id, name = id, price = 100)

    @Test
    fun `новый элемент встаёт в начало`() {
        val list = RecentMru.push(listOf(item("a"), item("b")), item("c"))
        assertEquals(listOf("c", "a", "b"), list.map { it.productId })
    }

    @Test
    fun `повторный просмотр поднимает наверх без дублей`() {
        val list = RecentMru.push(listOf(item("a"), item("b"), item("c")), item("c"))
        assertEquals(listOf("c", "a", "b"), list.map { it.productId })
    }

    @Test
    fun `список ограничен MAX`() {
        var list = emptyList<RecentProduct>()
        repeat(15) { i -> list = RecentMru.push(list, item("p$i")) }
        assertEquals(RecentMru.MAX, list.size)
        assertEquals("p14", list.first().productId)
        assertEquals("p5", list.last().productId)
    }
}
