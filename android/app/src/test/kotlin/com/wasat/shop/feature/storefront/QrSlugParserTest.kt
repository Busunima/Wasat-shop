package com.wasat.shop.feature.storefront

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrSlugParserTest {

    @Test
    fun `deep link myapp scheme`() {
        assertEquals("sneaker-hub", QrSlugParser.parse("myapp://store/sneaker-hub"))
        assertEquals("sneaker-hub", QrSlugParser.parse("myapp://store/sneaker-hub?ref=qr"))
    }

    @Test
    fun `web app link`() {
        assertEquals("my-shop", QrSlugParser.parse("https://app.example.com/s/my-shop"))
        assertEquals("my-shop", QrSlugParser.parse("http://app.example.com/s/my-shop/"))
    }

    @Test
    fun `голый slug нормализуется и валидируется`() {
        assertEquals("my-shop", QrSlugParser.parse("My-Shop"))
        assertEquals("abc123", QrSlugParser.parse(" abc123 "))
    }

    @Test
    fun `мусор и невалидный slug — null`() {
        assertNull(QrSlugParser.parse(null))
        assertNull(QrSlugParser.parse(""))
        assertNull(QrSlugParser.parse("https://google.com"))
        assertNull(QrSlugParser.parse("ab")) // короче 3
        assertNull(QrSlugParser.parse("myapp://store/-bad-"))
        assertNull(QrSlugParser.parse("просто текст"))
    }
}
