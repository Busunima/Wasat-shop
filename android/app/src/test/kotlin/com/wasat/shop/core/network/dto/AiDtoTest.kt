package com.wasat.shop.core.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `AiDescribeRequest - null-поля опущены, дефолты сервер подставит`() {
        val minimal = json.encodeToString(AiDescribeRequest(name = "Кеды"))
        assertTrue(minimal.contains("\"name\":\"Кеды\""))
        assertFalse(minimal.contains("category"))
        assertFalse(minimal.contains("hints"))

        val full = json.encodeToString(
            AiDescribeRequest(name = "Кеды", category = "Обувь", tags = listOf("лето"), hints = "замша"),
        )
        assertTrue(full.contains("\"category\":\"Обувь\""))
        assertTrue(full.contains("\"hints\":\"замша\""))
    }

    @Test
    fun `AiDescribeResponse - декодирование`() {
        val decoded = json.decodeFromString<AiDescribeResponse>("""{"description":"Текст"}""")
        assertEquals("Текст", decoded.description)
    }
}
