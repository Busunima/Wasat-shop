package com.wasat.shop.feature.admin

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsValidationTest {

    @Test
    fun `hex - строго #RRGGBB, пусто допустимо`() {
        assertNull(SettingsValidation.validateHex("#2D4A7C"))
        assertNull(SettingsValidation.validateHex("#aabbcc"))
        assertNull(SettingsValidation.validateHex("")) // тема не задаётся
        assertNotNull(SettingsValidation.validateHex("2D4A7C"))
        assertNotNull(SettingsValidation.validateHex("#2D4A7"))
        assertNotNull(SettingsValidation.validateHex("#2D4A7CC"))
        assertNotNull(SettingsValidation.validateHex("#GGGGGG"))
    }

    @Test
    fun `email - базовая проверка, пусто допустимо`() {
        assertNull(SettingsValidation.validateEmail("shop@example.com"))
        assertNull(SettingsValidation.validateEmail(""))
        assertNotNull(SettingsValidation.validateEmail("плохой"))
        assertNotNull(SettingsValidation.validateEmail("a@b"))
        assertNotNull(SettingsValidation.validateEmail("a b@c.com"))
    }

    @Test
    fun `url - http-s, пусто допустимо (FR-A01 сайт)`() {
        assertNull(SettingsValidation.validateUrl("https://shop.example.com"))
        assertNull(SettingsValidation.validateUrl("http://shop.test/path"))
        assertNull(SettingsValidation.validateUrl("")) // сайт не задаётся
        assertNotNull(SettingsValidation.validateUrl("shop.example.com"))
        assertNotNull(SettingsValidation.validateUrl("ftp://x"))
        assertNotNull(SettingsValidation.validateUrl("просто текст"))
    }
}
