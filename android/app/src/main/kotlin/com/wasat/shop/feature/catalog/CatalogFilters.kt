package com.wasat.shop.feature.catalog

/** Сортировки каталога — зеркало PRODUCT_SORTS сервера (FR-B02). */
enum class CatalogSort(val wire: String) {
    NEW("new"),
    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc"),
    RATING("rating"),
}

/**
 * Состояние фильтров каталога (FR-B02). Цены — в минорных единицах валюты магазина.
 * Pure JVM — toQueryMap покрыт unit-тестом.
 */
data class CatalogFilters(
    val query: String = "",
    val category: String? = null,
    val inStockOnly: Boolean = false,
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
    val sort: CatalogSort = CatalogSort.NEW,
) {
    val isDefault: Boolean
        get() = this == CatalogFilters(query = query) // фильтры, кроме поиска

    /** Query-параметры GET /stores/:id/products (cursor/limit добавляет PagingSource). */
    fun toQueryMap(): Map<String, String> = buildMap {
        if (query.isNotBlank()) put("q", query.trim())
        category?.let { put("category", it) }
        if (inStockOnly) put("inStock", "true")
        minPrice?.let { put("minPrice", it.toString()) }
        maxPrice?.let { put("maxPrice", it.toString()) }
        if (sort != CatalogSort.NEW) put("sort", sort.wire)
    }
}
