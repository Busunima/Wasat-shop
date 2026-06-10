package com.wasat.shop.feature.admin

import com.wasat.shop.core.network.dto.ProductUpsertRequest
import com.wasat.shop.core.network.dto.VariantDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Маппинг черновиков вариантов и сериализация тела upsert ↔ контракт сервера. */
class ProductUpsertTest {

    private val json = Json { ignoreUnknownKeys = true } // как в NetworkModule

    @Test
    fun `VariantDraft - валидный маппится, пустые size-color становятся null`() {
        val dto = VariantDraft(size = " M ", color = "", stockInput = "3").toVariantDto()
        assertEquals(VariantDto(size = "M", color = null, stock = 3), dto)
    }

    @Test
    fun `VariantDraft - невалидный stock даёт null`() {
        assertNull(VariantDraft(stockInput = "").toVariantDto())
        assertNull(VariantDraft(stockInput = "-1").toVariantDto())
        assertNull(VariantDraft(stockInput = "x").toVariantDto())
    }

    @Test
    fun `upsert - description, images и variants кодируются всегда (PATCH очищает поля)`() {
        val encoded = json.encodeToString(
            ProductUpsertRequest.serializer(),
            ProductUpsertRequest(
                name = "X",
                price = 100,
                description = "",
                status = "draft",
                images = emptyList(),
                variants = emptyList(),
            ),
        )
        assertTrue(encoded, "\"description\":\"\"" in encoded)
        assertTrue(encoded, "\"images\":[]" in encoded)
        assertTrue(encoded, "\"variants\":[]" in encoded)
        // originalPrice отсутствует в форме — null опускается
        assertFalse(encoded, "originalPrice" in encoded)
    }

    @Test
    fun `upsert - заполненные фото и варианты сериализуются`() {
        val encoded = json.encodeToString(
            ProductUpsertRequest.serializer(),
            ProductUpsertRequest(
                name = "Кеды",
                price = 9_990,
                description = "Описание",
                status = "active",
                images = listOf("https://storage.test/p1.png"),
                variants = listOf(VariantDto(size = "42", stock = 5)),
            ),
        )
        assertTrue(encoded, "https://storage.test/p1.png" in encoded)
        assertTrue(encoded, "\"stock\":5" in encoded)
    }
}
