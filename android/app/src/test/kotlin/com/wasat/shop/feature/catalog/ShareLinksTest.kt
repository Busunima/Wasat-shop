package com.wasat.shop.feature.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

/** Публичная ссылка витрины для шеринга (FR-B12). */
class ShareLinksTest {

    @Test
    fun `storeUrl строит App Link витрины по slug`() {
        assertEquals("https://app.example.com/s/sneaker-hub", ShareLinks.storeUrl("sneaker-hub"))
    }
}
