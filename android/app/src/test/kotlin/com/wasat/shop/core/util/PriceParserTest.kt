package com.wasat.shop.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceParserTest {

    @Test
    fun `USD - точка и запятая как разделитель`() {
        assertEquals(12_990L, PriceParser.parse("129.90", "USD"))
        assertEquals(12_990L, PriceParser.parse("129,90", "USD"))
        assertEquals(12_900L, PriceParser.parse("129", "USD"))
        assertEquals(0L, PriceParser.parse("0", "USD"))
    }

    @Test
    fun `JPY - без дробной части`() {
        assertEquals(500L, PriceParser.parse("500", "JPY"))
        // лишние знаки после запятой для валюты без копеек — некорректный ввод
        assertNull(PriceParser.parse("500.50", "JPY"))
    }

    @Test
    fun `слишком много знаков после запятой - ошибка`() {
        assertNull(PriceParser.parse("129.999", "USD"))
    }

    @Test
    fun `мусор, пустота и отрицательные - ошибка`() {
        assertNull(PriceParser.parse("", "USD"))
        assertNull(PriceParser.parse("abc", "USD"))
        assertNull(PriceParser.parse("-5", "USD"))
        assertNull(PriceParser.parse("12.3.4", "USD"))
    }

    @Test
    fun `пробелы и хвостовые нули терпимы`() {
        assertEquals(12_990L, PriceParser.parse(" 129.90 ", "USD"))
        assertEquals(12_900L, PriceParser.parse("129.00", "USD"))
    }

    @Test
    fun `round-trip с PriceFormatter`() {
        val minor = PriceParser.parse("129.90", "USD")!!
        assertEquals("$129.90", PriceFormatter.format(minor, "USD", java.util.Locale.US))
    }
}
