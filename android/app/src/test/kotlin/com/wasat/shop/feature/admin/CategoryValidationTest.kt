package com.wasat.shop.feature.admin

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryValidationTest {

    @Test
    fun `name - 1-80 символов, пусто недопустимо`() {
        assertNull(CategoryValidation.validateName("Обувь"))
        assertNotNull(CategoryValidation.validateName(""))
        assertNotNull(CategoryValidation.validateName("   "))
        assertNotNull(CategoryValidation.validateName("a".repeat(81)))
    }

    @Test
    fun `slug - строчная латиница-цифры-дефис, 2-40`() {
        assertNull(CategoryValidation.validateSlug("shoes"))
        assertNull(CategoryValidation.validateSlug("new-arrivals-2024"))
        assertNotNull(CategoryValidation.validateSlug("a")) // короче 2
        assertNotNull(CategoryValidation.validateSlug("Shoes")) // верхний регистр
        assertNotNull(CategoryValidation.validateSlug("обувь")) // кириллица
        assertNotNull(CategoryValidation.validateSlug("-shoes")) // дефис по краю
        assertNotNull(CategoryValidation.validateSlug("a".repeat(41)))
    }
}
