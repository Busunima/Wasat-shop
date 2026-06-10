package com.wasat.shop.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.wasat.shop.R
import com.wasat.shop.core.designsystem.LocalWindowWidthSizeClass
import com.wasat.shop.core.designsystem.isExpandedLayout
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.util.PriceFormatter

/** Каталог витрины (FR-B02 базово): сетка товаров, адаптивное число колонок. */
@Composable
fun CatalogScreen(
    currency: String,
    onProductClick: (productId: String) -> Unit,
    onOpenCart: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val cartCount by viewModel.cartCount.collectAsState()
    val columns = if (LocalWindowWidthSizeClass.current.isExpandedLayout) 3 else 2

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.catalog_title),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onOpenCart) {
                Text(stringResource(R.string.cart_open, cartCount))
            }
        }
        CatalogContent(
            state = state,
            columns = columns,
            currency = currency,
            onProductClick = onProductClick,
            onRetry = viewModel::load,
        )
    }
}

@Composable
private fun CatalogContent(
    state: CatalogUiState,
    columns: Int,
    currency: String,
    onProductClick: (productId: String) -> Unit,
    onRetry: () -> Unit,
) {
    when (val s = state) {
        CatalogUiState.Loading -> Centered { CircularProgressIndicator() }

        is CatalogUiState.Error -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.catalog_retry))
                }
            }
        }

        is CatalogUiState.Loaded -> if (s.products.isEmpty()) {
            Centered {
                Text(
                    text = stringResource(R.string.catalog_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(s.products, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        currency = currency,
                        onClick = { onProductClick(product.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: ProductDto, currency: String, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            AsyncImage(
                model = product.images.firstOrNull(),
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = PriceFormatter.format(product.price, currency),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}
