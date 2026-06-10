package com.wasat.shop.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Формы M3 Expressive (ТЗ §11.2): увеличенные радиусы скругления относительно
 * дефолтных M3 — выразительные карточки/диалоги при сдержанных мелких элементах.
 */
internal val WasatShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
