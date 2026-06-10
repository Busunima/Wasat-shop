package com.wasat.shop.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Токены движения M3 Expressive (ТЗ §11.2): длительности и кривые.
 * Emphasized — для переходов между экранами и крупных элементов,
 * Standard — для мелких внутриэкранных изменений.
 */
object WasatMotion {
    // Длительности (мс)
    const val DURATION_SHORT = 200
    const val DURATION_MEDIUM = 350
    const val DURATION_LONG = 500

    // Кривые M3
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
}
