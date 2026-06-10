package com.wasat.shop.feature.admin

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProductFormValidationTest {

    @Test
    fun `name - границы 1 и 200`() {
        assertNull(ProductFormValidation.validateName("X"))
        assertNull(ProductFormValidation.validateName("x".repeat(200)))
        assertNotNull(ProductFormValidation.validateName(""))
        assertNotNull(ProductFormValidation.validateName("  "))
        assertNotNull(ProductFormValidation.validateName("x".repeat(201)))
    }

    @Test
    fun `price - через PriceParser`() {
        assertNull(ProductFormValidation.validatePrice("129.90", "USD"))
        assertNotNull(ProductFormValidation.validatePrice("", "USD"))
        assertNotNull(ProductFormValidation.validatePrice("abc", "USD"))
        assertNotNull(ProductFormValidation.validatePrice("-1", "USD"))
    }

    @Test
    fun `description - до 5000`() {
        assertNull(ProductFormValidation.validateDescription(""))
        assertNull(ProductFormValidation.validateDescription("x".repeat(5000)))
        assertNotNull(ProductFormValidation.validateDescription("x".repeat(5001)))
    }

    @Test
    fun `stock - целое неотрицательное`() {
        assertNull(ProductFormValidation.validateStock("0"))
        assertNull(ProductFormValidation.validateStock("42"))
        assertNull(ProductFormValidation.validateStock(" 7 "))
        assertNotNull(ProductFormValidation.validateStock(""))
        assertNotNull(ProductFormValidation.validateStock("-1"))
        assertNotNull(ProductFormValidation.validateStock("1.5"))
        assertNotNull(ProductFormValidation.validateStock("abc"))
    }
}
