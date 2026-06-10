package com.wasat.shop.core.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceFormatterTest {

    @Test
    fun `минорные единицы конвертируются по дробности валюты`() {
        // USD: 2 знака — 12990 центов = $129.90
        assertEquals("$129.90", PriceFormatter.format(12_990, "USD", Locale.US))
        // JPY: 0 знаков — 500 йен остаются 500
        val jpy = PriceFormatter.format(500, "JPY", Locale.US)
        assertTrue(jpy, "500" in jpy && "5.00" !in jpy)
    }

    @Test
    fun `нулевая и крупная суммы`() {
        assertEquals("$0.00", PriceFormatter.format(0, "USD", Locale.US))
        assertEquals("$10,000.00", PriceFormatter.format(1_000_000, "USD", Locale.US))
    }

    @Test
    fun `неизвестная валюта не падает`() {
        assertEquals("100 ZZZ", PriceFormatter.format(100, "ZZZ", Locale.US))
    }
}
