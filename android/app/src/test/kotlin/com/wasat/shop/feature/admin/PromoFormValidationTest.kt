package com.wasat.shop.feature.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PromoFormValidationTest {

    @Test
    fun `normalizeCode - верхний регистр и trim`() {
        assertEquals("SALE-10", PromoFormValidation.normalizeCode(" sale-10 "))
    }

    @Test
    fun `code - длина и допустимые символы`() {
        assertNull(PromoFormValidation.validateCode("SALE-10"))
        assertNull(PromoFormValidation.validateCode("abc")) // нормализуется в ABC
        assertNotNull(PromoFormValidation.validateCode("ab")) // короче 3
        assertNotNull(PromoFormValidation.validateCode("a".repeat(33))) // длиннее 32
        assertNotNull(PromoFormValidation.validateCode("-abc")) // начинается с дефиса
        assertNotNull(PromoFormValidation.validateCode("SA LE")) // пробел внутри
    }

    @Test
    fun `value percent - 1 до 100`() {
        assertNull(PromoFormValidation.validateValue("percent", "1", "USD"))
        assertNull(PromoFormValidation.validateValue("percent", "100", "USD"))
        assertNotNull(PromoFormValidation.validateValue("percent", "0", "USD"))
        assertNotNull(PromoFormValidation.validateValue("percent", "101", "USD"))
        assertNotNull(PromoFormValidation.validateValue("percent", "abc", "USD"))
    }

    @Test
    fun `value fixed - валидная сумма`() {
        assertNull(PromoFormValidation.validateValue("fixed", "5.00", "USD"))
        assertNotNull(PromoFormValidation.validateValue("fixed", "", "USD"))
        assertNotNull(PromoFormValidation.validateValue("fixed", "-1", "USD"))
    }

    @Test
    fun `value free_shipping - игнорируется`() {
        assertNull(PromoFormValidation.validateValue("free_shipping", "", "USD"))
    }

    @Test
    fun `minAmount - пусто ок, иначе валидная сумма`() {
        assertNull(PromoFormValidation.validateMinAmount("", "USD"))
        assertNull(PromoFormValidation.validateMinAmount("50.00", "USD"))
        assertNotNull(PromoFormValidation.validateMinAmount("abc", "USD"))
    }

    @Test
    fun `usageLimit - пусто ок, иначе целое не меньше 1`() {
        assertNull(PromoFormValidation.validateUsageLimit(""))
        assertNull(PromoFormValidation.validateUsageLimit("100"))
        assertNotNull(PromoFormValidation.validateUsageLimit("0"))
        assertNotNull(PromoFormValidation.validateUsageLimit("abc"))
    }
}
