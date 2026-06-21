package com.wasat.shop.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Аудит контраста токенов темы (ТЗ §11.2: контраст ≥ WCAG AA). Считаем коэффициент
 * контраста по WCAG 2.1 для ключевых пар «текст/фон» светлой и тёмной тем; нормальный
 * текст должен иметь ≥ 4.5:1. Pure JVM — без Android-рантайма.
 */
class ContrastTest {

    private fun linear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    /** Коэффициент контраста WCAG: (L_светл + 0.05) / (L_тёмн + 0.05). */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertAA(a: Color, b: Color, pair: String) {
        val ratio = contrast(a, b)
        assertTrue("Контраст $pair = ${"%.2f".format(ratio)} < 4.5 (WCAG AA)", ratio >= 4.5)
    }

    @Test
    fun lightTheme_textOnBackground_meetsAA() {
        assertAA(LightOnBackground, LightBackground, "onBackground/background (light)")
    }

    @Test
    fun darkTheme_textOnBackground_meetsAA() {
        assertAA(DarkOnBackground, DarkBackground, "onBackground/background (dark)")
    }

    @Test
    fun lightTheme_textOnPrimary_meetsAA() {
        assertAA(LightOnPrimary, LightPrimary, "onPrimary/primary (light)")
    }

    @Test
    fun darkTheme_textOnPrimary_meetsAA() {
        assertAA(DarkOnPrimary, DarkPrimary, "onPrimary/primary (dark)")
    }
}
