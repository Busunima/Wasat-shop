package com.wasat.shop.feature.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Ряд из 5 звёзд символами ★/☆ (без icons-зависимости). onRate != null —
 * интерактивный выбор рейтинга; иначе — отображение приближения рейтинга. Pure UI.
 */
@Composable
fun StarRow(
    rating: Int,
    modifier: Modifier = Modifier,
    starSize: TextUnit = 20.sp,
    onRate: ((Int) -> Unit)? = null,
) {
    Row(modifier = modifier) {
        (1..5).forEach { star ->
            val filled = star <= rating
            Text(
                text = if (filled) "★" else "☆",
                style = TextStyle(fontSize = starSize),
                color = if (filled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = if (onRate != null) Modifier.clickable { onRate(star) } else Modifier,
            )
        }
    }
}
