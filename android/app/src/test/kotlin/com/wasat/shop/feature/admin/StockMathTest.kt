package com.wasat.shop.feature.admin

import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.VariantDto
import org.junit.Assert.assertEquals
import org.junit.Test

/** Оптимистичный пересчёт остатка (B5.3): дельта по товару/варианту, clamp ≥ 0. */
class StockMathTest {

    private fun product(id: String, totalStock: Int, variants: List<VariantDto> = emptyList()) =
        ProductDto(id = id, name = "P", price = 100, totalStock = totalStock, variants = variants)

    @Test
    fun `товар без вариантов — меняется totalStock`() {
        val r = StockMath.applyDelta(listOf(product("p1", 5)), "p1", null, null, null, 3)
        assertEquals(8, r.first().totalStock)
    }

    @Test
    fun `остаток не уходит ниже нуля`() {
        val r = StockMath.applyDelta(listOf(product("p1", 2)), "p1", null, null, null, -5)
        assertEquals(0, r.first().totalStock)
    }

    @Test
    fun `вариант по sku — корректируется и totalStock пересчитан`() {
        val items = listOf(
            product("p1", 5, listOf(VariantDto(stock = 2, sku = "A"), VariantDto(stock = 3, sku = "B"))),
        )
        val p = StockMath.applyDelta(items, "p1", "A", null, null, 4).first()
        assertEquals(6, p.variants[0].stock)
        assertEquals(3, p.variants[1].stock)
        assertEquals(9, p.totalStock)
    }

    @Test
    fun `другие товары не трогаются`() {
        val items = listOf(product("p1", 5), product("p2", 7))
        val r = StockMath.applyDelta(items, "p1", null, null, null, 1)
        assertEquals(7, r[1].totalStock)
    }
}
