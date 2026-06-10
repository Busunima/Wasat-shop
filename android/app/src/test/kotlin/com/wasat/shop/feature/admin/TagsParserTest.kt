package com.wasat.shop.feature.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TagsParserTest {

    @Test
    fun `parse - trim, пустые отбрасываются, дубликаты схлопываются`() {
        assertEquals(
            listOf("обувь", "лето", "sale"),
            TagsParser.parse(" обувь, лето ,, sale, обувь "),
        )
        assertEquals(emptyList<String>(), TagsParser.parse(""))
        assertEquals(emptyList<String>(), TagsParser.parse(" , , "))
    }

    @Test
    fun `validate - лимиты количества и длины`() {
        assertNull(TagsParser.validate("a, b, c"))
        assertNull(TagsParser.validate(""))
        assertNotNull(TagsParser.validate((1..21).joinToString(",") { "tag$it" }))
        assertNotNull(TagsParser.validate("x".repeat(41)))
    }

    @Test
    fun `format - round-trip с parse`() {
        val tags = listOf("обувь", "лето")
        assertEquals(tags, TagsParser.parse(TagsParser.format(tags)))
    }
}
