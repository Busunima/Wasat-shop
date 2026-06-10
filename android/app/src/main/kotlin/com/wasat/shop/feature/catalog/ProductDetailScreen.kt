package com.wasat.shop.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.core.designsystem.LocalWindowWidthSizeClass
import com.wasat.shop.core.designsystem.ProductImage
import com.wasat.shop.core.designsystem.ZoomableImageDialog
import com.wasat.shop.core.designsystem.isExpandedLayout
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.VariantDto
import com.wasat.shop.core.util.ImageUrls
import com.wasat.shop.core.util.PriceFormatter

/** Карточка товара (FR-B03): пейджер фото + zoom, варианты, наличие, отзывы. */
@Composable
fun ProductDetailScreen(
    currency: String,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    when (val s = state) {
        ProductDetailUiState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        is ProductDetailUiState.Error -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = viewModel::load) {
                    Text(stringResource(R.string.catalog_retry))
                }
            }
        }

        is ProductDetailUiState.Loaded -> ProductDetailContent(
            product = s.product,
            currency = currency,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun ProductDetailContent(
    product: ProductDto,
    currency: String,
    viewModel: ProductDetailViewModel,
) {
    val justAdded by viewModel.justAdded.collectAsState()
    var selectedVariant by remember { mutableStateOf(product.variants.firstOrNull { it.stock > 0 }) }
    var zoomedImageUrl by remember { mutableStateOf<String?>(null) }

    zoomedImageUrl?.let { url ->
        ZoomableImageDialog(
            url = url,
            contentDescription = product.name,
            onDismiss = { zoomedImageUrl = null },
        )
    }

    val widthModifier = if (LocalWindowWidthSizeClass.current.isExpandedLayout) {
        Modifier.widthIn(max = 640.dp)
    } else {
        Modifier.fillMaxWidth()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = widthModifier.verticalScroll(rememberScrollState()),
        ) {
            PhotoPager(product = product, onPhotoTap = { zoomedImageUrl = it })

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(product.name, style = MaterialTheme.typography.headlineSmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = PriceFormatter.format(product.price, currency),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    product.originalPrice?.let { old ->
                        Text(
                            text = PriceFormatter.format(old, currency),
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                if (product.variants.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        product.variants.take(6).forEach { variant ->
                            FilterChip(
                                selected = variant == selectedVariant,
                                onClick = { selectedVariant = variant },
                                label = {
                                    Text(
                                        listOfNotNull(variant.size, variant.color)
                                            .joinToString(" · ")
                                            .ifEmpty { variant.sku ?: "—" },
                                    )
                                },
                                enabled = variant.stock > 0,
                            )
                        }
                    }
                }

                StockIndicator(product = product, selectedVariant = selectedVariant)

                Button(
                    onClick = { viewModel.addToCart(currency, selectedVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = StockInfo.availableStock(product, selectedVariant) > 0,
                ) {
                    Text(
                        stringResource(
                            if (justAdded) R.string.cart_added else R.string.cart_add,
                        ),
                    )
                }

                if (product.description.isNotBlank()) {
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                ReviewsSection()
            }
        }
    }
}

/** Галерея фото: HorizontalPager + точки-индикатор; тап — полноэкранный zoom (FR-B03). */
@Composable
private fun PhotoPager(product: ProductDto, onPhotoTap: (String) -> Unit) {
    if (product.images.size <= 1) {
        ProductImage(
            url = product.images.firstOrNull(),
            contentDescription = product.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(enabled = product.images.isNotEmpty()) {
                    product.images.firstOrNull()?.let(onPhotoTap)
                },
            size = ImageUrls.MEDIUM,
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { product.images.size })
    Box {
        HorizontalPager(state = pagerState) { page ->
            val url = product.images[page]
            ProductImage(
                url = url,
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable { onPhotoTap(url) },
                size = ImageUrls.MEDIUM,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(product.images.size) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }
    }
}

/** Наличие по выбранному варианту (FR-B03). */
@Composable
private fun StockIndicator(product: ProductDto, selectedVariant: VariantDto?) {
    val stock = StockInfo.availableStock(product, selectedVariant)
    val (textRes, color) = when {
        stock <= 0 -> R.string.product_out_of_stock to MaterialTheme.colorScheme.error
        stock <= StockInfo.LOW_STOCK_THRESHOLD ->
            R.string.product_low_stock to MaterialTheme.colorScheme.error
        else -> R.string.product_in_stock to MaterialTheme.colorScheme.primary
    }
    Text(
        text = if (stock in 1..StockInfo.LOW_STOCK_THRESHOLD) {
            stringResource(textRes, stock)
        } else {
            stringResource(textRes)
        },
        color = color,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** Отзывы (FR-B03/FR-B08): каркас — полноценные отзывы требуют доставленных заказов. */
@Composable
private fun ReviewsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(
            text = stringResource(R.string.product_reviews_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.product_reviews_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
