package com.wasat.shop.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.wasat.shop.R
import com.wasat.shop.core.designsystem.LocalWindowWidthSizeClass
import com.wasat.shop.core.designsystem.isExpandedLayout
import com.wasat.shop.core.util.PriceFormatter

/** Карточка товара (FR-B03 базово): галерея, цена/старая цена, варианты, описание. */
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

        is ProductDetailUiState.Loaded -> {
            val product = s.product
            val justAdded by viewModel.justAdded.collectAsState()
            var selectedVariant by remember { mutableStateOf(product.variants.firstOrNull { it.stock > 0 }) }
            val widthModifier = if (LocalWindowWidthSizeClass.current.isExpandedLayout) {
                Modifier.widthIn(max = 640.dp)
            } else {
                Modifier.fillMaxWidth()
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = widthModifier.verticalScroll(rememberScrollState()),
                ) {
                    AsyncImage(
                        model = product.images.firstOrNull(),
                        contentDescription = product.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentScale = ContentScale.Crop,
                    )
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

                        if (product.totalStock <= 0) {
                            Text(
                                text = stringResource(R.string.product_out_of_stock),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        Button(
                            onClick = { viewModel.addToCart(currency, selectedVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            // Товар с вариантами требует выбранного варианта в наличии
                            enabled = product.totalStock > 0 &&
                                (product.variants.isEmpty() || (selectedVariant?.stock ?: 0) > 0),
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
                    }
                }
            }
        }
    }
}
