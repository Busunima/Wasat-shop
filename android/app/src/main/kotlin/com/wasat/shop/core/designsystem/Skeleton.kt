package com.wasat.shop.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Skeleton-плейсхолдеры (§11.6): анимированный shimmer вместо центрального спиннера на
 * экранах-списках. Снижает воспринимаемую задержку — каркас контента виден сразу.
 */

/** Анимированный shimmer-фон: бегущий горизонтальный градиент поверх нейтральной базы. */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = WasatMotion.StandardEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-translate",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val width = 320f
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(width * translate, 0f),
        end = Offset(width * (translate + 1f), 0f),
    )
    return this.background(brush)
}

/** Прямоугольный блок-заглушка со скруглением и shimmer. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, cornerRadius: Int = 8) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .shimmer(),
    )
}

/** Карточка-заглушка товара: повторяет раскладку реальной карточки каталога. */
@Composable
fun ProductCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 0,
        )
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth().height(14.dp))
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp))
        }
    }
}

/** Сетка карточек-заглушек на время первичной загрузки каталога (§11.6). */
@Composable
fun ProductGridSkeleton(columns: Int, modifier: Modifier = Modifier, itemCount: Int = 6) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(itemCount) {
            ProductCardSkeleton()
        }
    }
}
