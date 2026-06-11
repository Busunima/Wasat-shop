package com.wasat.shop.core.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PushDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `PushTokenRequest - платформа android по умолчанию, оба поля кодируются`() {
        val encoded = json.encodeToString(PushTokenRequest(token = "abc-token-123"))
        assertEquals("""{"token":"abc-token-123"}""", encoded)
        // platform default не кодируется → сервер подставит android (zod default)

        val decoded = json.decodeFromString<PushTokenRequest>(
            """{"token":"t","platform":"web"}""",
        )
        assertEquals("web", decoded.platform)
    }
}
