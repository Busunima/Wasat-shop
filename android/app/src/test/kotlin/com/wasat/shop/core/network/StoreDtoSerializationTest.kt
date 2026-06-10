package com.wasat.shop.core.network

import com.wasat.shop.core.network.dto.ErrorEnvelope
import com.wasat.shop.core.network.dto.StoreInitRequest
import com.wasat.shop.core.network.dto.StoreInitResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Сериализация DTO ↔ контракт сервера (docs/api-contract.md). */
class StoreDtoSerializationTest {

    private val json = Json { ignoreUnknownKeys = true } // как в NetworkModule

    @Test
    fun `запрос без description не содержит ключа description`() {
        val encoded = json.encodeToString(
            StoreInitRequest.serializer(),
            StoreInitRequest(name = "Shop", slug = "shop", currency = "USD"),
        )
        // zod-схема: description optional — null не должен попадать в тело
        assertFalse(encoded.contains("description"))
    }

    @Test
    fun `запрос round-trip со всеми полями`() {
        val request = StoreInitRequest("My Shop", "my-shop", "EUR", "Описание")
        val decoded = json.decodeFromString(
            StoreInitRequest.serializer(),
            json.encodeToString(StoreInitRequest.serializer(), request),
        )
        assertEquals(request, decoded)
    }

    @Test
    fun `ответ с onboarding deferred=true`() {
        val payload = """
            {"storeId":"123e4567-e89b-12d3-a456-426614174000","slug":"my-shop",
             "onboarding":{"deferred":true,"reason":"Stripe не сконфигурирован"}}
        """.trimIndent()
        val response = json.decodeFromString(StoreInitResponse.serializer(), payload)
        assertEquals("my-shop", response.slug)
        assertEquals(true, response.onboarding.deferred)
        assertEquals("Stripe не сконфигурирован", response.onboarding.reason)
        assertNull(response.onboarding.onboardUrl)
    }

    @Test
    fun `ответ с onboarding deferred=false (Stripe-ветка)`() {
        val payload = """
            {"storeId":"id-1","slug":"s-1",
             "onboarding":{"deferred":false,"stripeAccountId":"acct_1","onboardUrl":"https://stripe.test/x"}}
        """.trimIndent()
        val response = json.decodeFromString(StoreInitResponse.serializer(), payload)
        assertEquals(false, response.onboarding.deferred)
        assertEquals("acct_1", response.onboarding.stripeAccountId)
        assertEquals("https://stripe.test/x", response.onboarding.onboardUrl)
    }

    @Test
    fun `ответ с неизвестными полями не ломает декодирование`() {
        val payload = """
            {"storeId":"id-1","slug":"s-1","extraField":42,
             "onboarding":{"deferred":true,"reason":"r","futureFlag":true}}
        """.trimIndent()
        val response = json.decodeFromString(StoreInitResponse.serializer(), payload)
        assertEquals("id-1", response.storeId)
    }

    @Test
    fun `конверт ошибки 409 CONFLICT декодируется`() {
        val payload = """
            {"error":{"code":"CONFLICT","message":"Slug уже занят","details":{"slug":"my-shop"}}}
        """.trimIndent()
        val envelope = json.decodeFromString(ErrorEnvelope.serializer(), payload)
        assertEquals("CONFLICT", envelope.error.code)
        assertEquals("Slug уже занят", envelope.error.message)
    }

    @Test
    fun `конверт ошибки без details декодируется`() {
        val payload = """{"error":{"code":"UNAUTHENTICATED","message":"Нет токена"}}"""
        val envelope = json.decodeFromString(ErrorEnvelope.serializer(), payload)
        assertEquals("UNAUTHENTICATED", envelope.error.code)
        assertNull(envelope.error.details)
    }
}
