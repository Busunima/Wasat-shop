package com.wasat.shop.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.wasat.shop.core.util.ImageUrls

/**
 * Изображение товара: сначала миниатюра по соглашению Resize-расширения,
 * при ошибке (миниатюра ещё не сгенерирована) — откат на оригинальный URL.
 */
@Composable
fun ProductImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: String = ImageUrls.THUMB,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var useOriginal by remember(url) { mutableStateOf(false) }
    val model = when {
        url == null -> null
        useOriginal -> url
        else -> ImageUrls.thumbnailUrl(url, size)
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onError = { if (!useOriginal && url != null) useOriginal = true },
    )
}
