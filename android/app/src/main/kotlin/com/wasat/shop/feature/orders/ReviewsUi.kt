package com.wasat.shop.feature.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.wasat.shop.R

/**
 * Ряд из 5 звёзд символами ★/☆ (без icons-зависимости). onRate != null —
 * интерактивный выбор рейтинга; иначе — отображение приближения рейтинга.
 * Доступность (ТЗ §11): в режиме показа ряд озвучивается одной подписью
 * «Рейтинг N из 5»; в интерактивном — каждая звезда подписана отдельно.
 */
@Composable
fun StarRow(
    rating: Int,
    modifier: Modifier = Modifier,
    starSize: TextUnit = 20.sp,
    onRate: ((Int) -> Unit)? = null,
) {
    val ratingLabel = stringResource(R.string.a11y_rating, rating)
    val rowModifier = if (onRate == null) {
        modifier.clearAndSetSemantics { contentDescription = ratingLabel }
    } else {
        modifier
    }
    Row(modifier = rowModifier) {
        (1..5).forEach { star ->
            val filled = star <= rating
            val starModifier = if (onRate != null) {
                val rateLabel = stringResource(R.string.a11y_rate_stars, star)
                Modifier
                    .clickable { onRate(star) }
                    .clearAndSetSemantics { contentDescription = rateLabel }
            } else {
                Modifier
            }
            Text(
                text = if (filled) "★" else "☆",
                style = TextStyle(fontSize = starSize),
                color = if (filled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = starModifier,
            )
        }
    }
}
