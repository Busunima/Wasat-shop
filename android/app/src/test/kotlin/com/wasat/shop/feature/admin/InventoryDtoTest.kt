package com.wasat.shop.feature.admin

import com.wasat.shop.core.network.dto.ImportReportDto
import com.wasat.shop.core.network.dto.StockAdjustRequest
import com.wasat.shop.core.network.dto.VariantSelectorDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Сериализация DTO инвентаря ↔ контракт сервера (FR-A03). */
class InventoryDtoTest {

    private val json = Json { ignoreUnknownKeys = true } // как в NetworkModule

    @Test
    fun `товар без вариантов - variant опускается`() {
        val encoded = json.encodeToString(
            StockAdjustRequest.serializer(),
            StockAdjustRequest(delta = -2),
        )
        assertFalse(encoded, "variant" in encoded)
        assertTrue(encoded, "\"delta\":-2" in encoded)
    }

    @Test
    fun `селектор по sku сериализуется, null-поля опускаются`() {
        val encoded = json.encodeToString(
            StockAdjustRequest.serializer(),
            StockAdjustRequest(variant = VariantSelectorDto(sku = "KEDY-41"), delta = 5),
        )
        assertTrue(encoded, "\"sku\":\"KEDY-41\"" in encoded)
        assertFalse(encoded, "size" in encoded)
    }

    @Test
    fun `отчёт импорта декодируется с ошибками`() {
        val payload = """
            {"applied":2,"errors":[{"line":3,"raw":"UNKNOWN,5","message":"sku не найден"}]}
        """.trimIndent()
        val report = json.decodeFromString(ImportReportDto.serializer(), payload)
        assertEquals(2, report.applied)
        assertEquals(3, report.errors.single().line)
    }
}
