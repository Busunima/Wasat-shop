package com.wasat.shop.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Базовые цветовые роли (ТЗ §11.1–11.2: нейтральная база + единый акцент).
 * Тема магазина (brand primary/secondary) накладывается поверх как один акцент.
 * Значения — стартовые; уточняются дизайн-системой в Фазе 1.
 */
internal val BrandPrimary = Color(0xFF2D4A7C)
internal val BrandSecondary = Color(0xFF5B6B82)

// Light
internal val LightPrimary = BrandPrimary
internal val LightSecondary = BrandSecondary
internal val LightBackground = Color(0xFFFCFCFF)
internal val LightSurface = Color(0xFFFCFCFF)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightOnBackground = Color(0xFF1A1C1E)

// Dark
internal val DarkPrimary = Color(0xFFADC6FF)
internal val DarkSecondary = Color(0xFFBFC6DC)
internal val DarkBackground = Color(0xFF1A1C1E)
internal val DarkSurface = Color(0xFF1A1C1E)
internal val DarkOnPrimary = Color(0xFF002E69)
internal val DarkOnBackground = Color(0xFFE2E2E6)
