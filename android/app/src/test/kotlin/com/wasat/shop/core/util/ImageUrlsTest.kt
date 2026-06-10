package com.wasat.shop.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUrlsTest {

    @Test
    fun `download-URL Firebase - кодированный путь, токен отбрасывается`() {
        val url = "https://firebasestorage.googleapis.com/v0/b/x.app/o/" +
            "stores%2Fs1%2Fproducts%2Fabc.jpg?alt=media&token=secret-token"
        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/x.app/o/" +
                "stores%2Fs1%2Fproducts%2Fabc_200x200.webp?alt=media",
            ImageUrls.thumbnailUrl(url),
        )
    }

    @Test
    fun `обычный путь со слешами и размером 800`() {
        assertEquals(
            "https://cdn.test/stores/s1/products/photo_800x800.webp",
            ImageUrls.thumbnailUrl("https://cdn.test/stores/s1/products/photo.png", ImageUrls.MEDIUM),
        )
    }

    @Test
    fun `файл без расширения`() {
        assertEquals(
            "https://cdn.test/p/uuid-1_200x200.webp",
            ImageUrls.thumbnailUrl("https://cdn.test/p/uuid-1"),
        )
    }
}
