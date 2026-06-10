package com.wasat.shop.feature.catalog

import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.VariantDto
import org.junit.Assert.assertEquals
import org.junit.Test

class StockInfoTest {

    private fun product(totalStock: Int, variants: List<VariantDto> = emptyList()) = ProductDto(
        id = "p1",
        name = "X",
        price = 100,
        totalStock = totalStock,
        variants = variants,
    )

    @Test
    fun `товар без вариантов - наличие по totalStock`() {
        assertEquals(7, StockInfo.availableStock(product(totalStock = 7), null))
        assertEquals(0, StockInfo.availableStock(product(totalStock = 0), null))
    }

    @Test
    fun `товар с вариантами - наличие по выбранному варианту`() {
        val m = VariantDto(size = "M", stock = 3)
        val l = VariantDto(size = "L", stock = 0)
        val p = product(totalStock = 3, variants = listOf(m, l))

        assertEquals(3, StockInfo.availableStock(p, m))
        assertEquals(0, StockInfo.availableStock(p, l))
    }

    @Test
    fun `вариант не выбран у вариантного товара - 0 (нельзя добавить в корзину)`() {
        val p = product(totalStock = 5, variants = listOf(VariantDto(size = "M", stock = 5)))
        assertEquals(0, StockInfo.availableStock(p, null))
    }
}
