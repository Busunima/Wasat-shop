package com.wasat.shop.feature.admin

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StaffFormValidationTest {

    @Test
    fun `email - валидные и невалидные`() {
        assertNull(StaffFormValidation.validateEmail("clerk@example.com"))
        assertNull(StaffFormValidation.validateEmail("  a.b+c@sub.domain.io  "))
        assertNotNull(StaffFormValidation.validateEmail(""))
        assertNotNull(StaffFormValidation.validateEmail("no-at-sign"))
        assertNotNull(StaffFormValidation.validateEmail("a@b"))
        assertNotNull(StaffFormValidation.validateEmail("a b@c.com"))
    }
}
