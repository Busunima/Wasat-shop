package com.wasat.shop.feature.catalog

import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.VariantDto

/**
 * Наличие по выбранному варианту (FR-B03): у товара с вариантами остаток берётся
 * из выбранного варианта, без вариантов — totalStock. Pure JVM — под unit-тестом.
 */
object StockInfo {
    fun availableStock(product: ProductDto, selectedVariant: VariantDto?): Int =
        if (product.variants.isEmpty()) product.totalStock else selectedVariant?.stock ?: 0

    /** Порог «осталось мало» для бейджа (UX-подсказка дефицита). */
    const val LOW_STOCK_THRESHOLD = 5
}
