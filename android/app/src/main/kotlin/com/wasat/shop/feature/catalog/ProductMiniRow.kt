package com.wasat.shop.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wasat.shop.core.designsystem.ProductImage
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.util.ImageUrls
import com.wasat.shop.core.util.PriceFormatter

/**
 * Горизонтальный ряд карточек товаров (FR-B12: «Похожие», «Популярное»). Скрыт, если
 * список пуст. Переиспользуется карточкой товара и каталогом.
 */
@Composable
fun ProductMiniRow(
    title: String,
    products: List<ProductDto>,
    currency: String,
    onProductClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (products.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(products, key = { it.id }) { product ->
                MiniCard(product = product, currency = currency, onClick = { onProductClick(product.id) })
            }
        }
    }
}

@Composable
private fun MiniCard(product: ProductDto, currency: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ProductImage(
            url = product.images.firstOrNull(),
            contentDescription = product.name,
            modifier = Modifier.size(120.dp),
            size = ImageUrls.THUMB,
        )
        Text(
            text = product.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = PriceFormatter.format(product.price, currency),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
