package com.wasat.shop.feature.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFiltersTest {

    @Test
    fun `дефолтные фильтры дают пустой query map`() {
        assertEquals(emptyMap<String, String>(), CatalogFilters().toQueryMap())
        assertTrue(CatalogFilters().isDefault)
    }

    @Test
    fun `полный набор фильтров сериализуется в параметры сервера`() {
        val filters = CatalogFilters(
            query = " кеды ",
            category = "Обувь",
            inStockOnly = true,
            minPrice = 1000,
            maxPrice = 5000,
            sort = CatalogSort.PRICE_DESC,
        )
        assertEquals(
            mapOf(
                "q" to "кеды",
                "category" to "Обувь",
                "inStock" to "true",
                "minPrice" to "1000",
                "maxPrice" to "5000",
                "sort" to "price_desc",
            ),
            filters.toQueryMap(),
        )
        assertFalse(filters.isDefault)
    }

    @Test
    fun `сортировка new и пустой поиск не передаются (дефолты сервера)`() {
        val map = CatalogFilters(query = "  ", sort = CatalogSort.NEW).toQueryMap()
        assertFalse("q" in map)
        assertFalse("sort" in map)
    }

    @Test
    fun `isDefault не учитывает поисковую строку`() {
        assertTrue(CatalogFilters(query = "abc").isDefault)
        assertFalse(CatalogFilters(query = "abc", inStockOnly = true).isDefault)
    }

    @Test
    fun `wire-значения сортировок — зеркало PRODUCT_SORTS сервера`() {
        assertEquals(
            listOf("new", "price_asc", "price_desc", "rating"),
            CatalogSort.entries.map { it.wire },
        )
    }
}
