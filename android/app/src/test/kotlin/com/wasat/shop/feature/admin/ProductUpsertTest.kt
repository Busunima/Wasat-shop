package com.wasat.shop.feature.admin

import com.wasat.shop.core.network.dto.ProductUpsertRequest
import com.wasat.shop.core.network.dto.VariantDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Маппинг черновиков вариантов и сериализация тела upsert ↔ контракт сервера. */
class ProductUpsertTest {

    private val json = Json { ignoreUnknownKeys = true } // как в NetworkModule

    private fun request(
        originalPrice: Long? = null,
        sku: String = "",
        tags: List<String> = emptyList(),
    ) = ProductUpsertRequest(
        name = "X",
        price = 100,
        description = "",
        status = "draft",
        images = emptyList(),
        variants = emptyList(),
        originalPrice = originalPrice,
        sku = sku,
        barcode = "",
        category = "",
        tags = tags,
    )

    @Test
    fun `VariantDraft - валидный маппится, пустые поля становятся null`() {
        val dto = VariantDraft(size = " M ", color = "", stockInput = "3", sku = " V-1 ").toVariantDto()
        assertEquals(VariantDto(size = "M", color = null, stock = 3, sku = "V-1"), dto)
    }

    @Test
    fun `VariantDraft - невалидный stock даёт null`() {
        assertNull(VariantDraft(stockInput = "").toVariantDto())
        assertNull(VariantDraft(stockInput = "-1").toVariantDto())
        assertNull(VariantDraft(stockInput = "x").toVariantDto())
    }

    @Test
    fun `upsert - все поля формы кодируются всегда (PATCH очищает поля)`() {
        val encoded = json.encodeToString(ProductUpsertRequest.serializer(), request())
        // форма владеет полным состоянием: очистка должна доехать до partial-PATCH
        assertTrue(encoded, "\"description\":\"\"" in encoded)
        assertTrue(encoded, "\"images\":[]" in encoded)
        assertTrue(encoded, "\"variants\":[]" in encoded)
        assertTrue(encoded, "\"originalPrice\":null" in encoded)
        assertTrue(encoded, "\"sku\":\"\"" in encoded)
        assertTrue(encoded, "\"barcode\":\"\"" in encoded)
        assertTrue(encoded, "\"category\":\"\"" in encoded)
        assertTrue(encoded, "\"tags\":[]" in encoded)
    }

    @Test
    fun `upsert - заполненные поля сериализуются`() {
        val encoded = json.encodeToString(
            ProductUpsertRequest.serializer(),
            request(originalPrice = 19_990, sku = "SKU-1", tags = listOf("обувь", "sale")),
        )
        assertTrue(encoded, "\"originalPrice\":19990" in encoded)
        assertTrue(encoded, "\"sku\":\"SKU-1\"" in encoded)
        assertTrue(encoded, "\"tags\":[\"обувь\",\"sale\"]" in encoded)
    }
}
