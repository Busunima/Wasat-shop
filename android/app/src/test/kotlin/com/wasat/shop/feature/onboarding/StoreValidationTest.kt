package com.wasat.shop.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Валидация — зеркало zod-схемы сервера (server/src/schemas/store.ts).
 * Кейсы регэкспа slug идентичны серверному ^[a-z0-9]+(?:-[a-z0-9]+)*$.
 */
class StoreValidationTest {

    // --- name: 1..120 ---

    @Test
    fun `name границы 1 и 120 валидны`() {
        assertNull(StoreValidation.validateName("a"))
        assertNull(StoreValidation.validateName("x".repeat(120)))
    }

    @Test
    fun `name пустое и длиннее 120 невалидны`() {
        assertNotNull(StoreValidation.validateName(""))
        assertNotNull(StoreValidation.validateName("   "))
        assertNotNull(StoreValidation.validateName("x".repeat(121)))
    }

    // --- slug: 3..40 + регэксп ---

    @Test
    fun `slug валидные формы`() {
        assertNull(StoreValidation.validateSlug("abc"))
        assertNull(StoreValidation.validateSlug("my-shop"))
        assertNull(StoreValidation.validateSlug("a1-b2-c3"))
        assertNull(StoreValidation.validateSlug("a".repeat(40)))
    }

    @Test
    fun `slug невалидные формы`() {
        assertNotNull("короче 3", StoreValidation.validateSlug("ab"))
        assertNotNull("длиннее 40", StoreValidation.validateSlug("a".repeat(41)))
        assertNotNull("ведущий дефис", StoreValidation.validateSlug("-abc"))
        assertNotNull("хвостовой дефис", StoreValidation.validateSlug("abc-"))
        assertNotNull("двойной дефис", StoreValidation.validateSlug("a--b"))
        assertNotNull("заглавные", StoreValidation.validateSlug("ABC"))
        assertNotNull("кириллица", StoreValidation.validateSlug("магазин"))
        assertNotNull("пробел", StoreValidation.validateSlug("my shop"))
    }

    // --- currency: 3 заглавные буквы ---

    @Test
    fun `currency валидные и невалидные коды`() {
        assertNull(StoreValidation.validateCurrency("USD"))
        assertNull(StoreValidation.validateCurrency("EUR"))
        assertNotNull(StoreValidation.validateCurrency("usd"))
        assertNotNull(StoreValidation.validateCurrency("US"))
        assertNotNull(StoreValidation.validateCurrency("USDD"))
        assertNotNull(StoreValidation.validateCurrency(""))
    }

    // --- description: ≤2000 ---

    @Test
    fun `description граница 2000`() {
        assertNull(StoreValidation.validateDescription(""))
        assertNull(StoreValidation.validateDescription("x".repeat(2000)))
        assertNotNull(StoreValidation.validateDescription("x".repeat(2001)))
    }

    // --- suggestSlug ---

    @Test
    fun `suggestSlug нормализует название`() {
        assertEquals("my-shop", StoreValidation.suggestSlug("My Shop!"))
        assertEquals("shop-42", StoreValidation.suggestSlug("  Shop   42  "))
        assertEquals("a-b", StoreValidation.suggestSlug("a---b"))
        assertEquals("", StoreValidation.suggestSlug("!!!"))
    }

    @Test
    fun `suggestSlug обрезает до 40 без хвостового дефиса`() {
        val long = ("ab ".repeat(20)).trim() // "ab ab ... ab" → slug длиннее 40 до обрезки
        val slug = StoreValidation.suggestSlug(long)
        assertEquals(true, slug.length <= 40)
        assertNull(StoreValidation.validateSlug(slug))
    }

    @Test
    fun `suggestSlug результат проходит validateSlug для обычных названий`() {
        listOf("Кофейня №1 (центр)", "John's Bikes", "Wasat Shop").forEach { name ->
            val slug = StoreValidation.suggestSlug(name)
            if (slug.length >= 3) {
                assertNull("из '$name' получился невалидный slug '$slug'", StoreValidation.validateSlug(slug))
            }
        }
    }
}
