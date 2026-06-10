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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.wasat.shop.R
import com.wasat.shop.core.designsystem.LocalWindowWidthSizeClass
import com.wasat.shop.core.designsystem.ProductImage
import com.wasat.shop.core.designsystem.isExpandedLayout
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.util.PriceFormatter

/** Каталог (FR-B02): поиск (debounce 300мс), фильтры, сортировка, Paging 3. */
@Composable
fun CatalogScreen(
    currency: String,
    onProductClick: (productId: String) -> Unit,
    onOpenCart: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val products = viewModel.products.collectAsLazyPagingItems()
    val searchInput by viewModel.searchInput.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val categories by viewModel.categories.collectAsState()
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

        // Поиск (FR-B02; debounce в ViewModel)
        OutlinedTextField(
            value = searchInput,
            onValueChange = viewModel::onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(stringResource(R.string.catalog_search_hint)) },
            singleLine = true,
        )

        FiltersRow(
            filters = filters,
            categories = categories,
            onSortChange = viewModel::onSortChange,
            onCategoryToggle = viewModel::onCategoryToggle,
            onInStockToggle = viewModel::onInStockToggle,
            onClear = viewModel::clearFilters,
        )

        CatalogGrid(
            products = products,
            columns = columns,
            currency = currency,
            onProductClick = onProductClick,
        )
    }
}

/** Чипы сортировки/фильтров одной прокручиваемой строкой. */
@Composable
private fun FiltersRow(
    filters: CatalogFilters,
    categories: Set<String>,
    onSortChange: (CatalogSort) -> Unit,
    onCategoryToggle: (String) -> Unit,
    onInStockToggle: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filters.inStockOnly,
            onClick = onInStockToggle,
            label = { Text(stringResource(R.string.catalog_filter_in_stock)) },
        )
        SORT_OPTIONS.forEach { (sort, labelRes) ->
            FilterChip(
                selected = filters.sort == sort,
                onClick = { onSortChange(sort) },
                label = { Text(stringResource(labelRes)) },
            )
        }
        categories.sorted().forEach { category ->
            FilterChip(
                selected = filters.category == category,
                onClick = { onCategoryToggle(category) },
                label = { Text(category) },
            )
        }
        if (!filters.isDefault) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.catalog_filter_clear))
            }
        }
    }
}

private val SORT_OPTIONS = listOf(
    CatalogSort.NEW to R.string.catalog_sort_new,
    CatalogSort.PRICE_ASC to R.string.catalog_sort_price_asc,
    CatalogSort.PRICE_DESC to R.string.catalog_sort_price_desc,
    CatalogSort.RATING to R.string.catalog_sort_rating,
)

@Composable
private fun CatalogGrid(
    products: LazyPagingItems<ProductDto>,
    columns: Int,
    currency: String,
    onProductClick: (productId: String) -> Unit,
) {
    val refresh = products.loadState.refresh
    when {
        refresh is LoadState.Loading -> Centered { CircularProgressIndicator() }

        refresh is LoadState.Error -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    refresh.error.message ?: stringResource(R.string.catalog_error),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = products::retry) {
                    Text(stringResource(R.string.catalog_retry))
                }
            }
        }

        products.itemCount == 0 -> Centered {
            Text(
                text = stringResource(R.string.catalog_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(products.itemCount, key = { products.peek(it)?.id ?: it }) { index ->
                products[index]?.let { product ->
                    ProductCard(
                        product = product,
                        currency = currency,
                        onClick = { onProductClick(product.id) },
                    )
                }
            }
            when (val append = products.loadState.append) {
                is LoadState.Loading -> item(span = { GridItemSpan(columns) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                is LoadState.Error -> item(span = { GridItemSpan(columns) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(onClick = products::retry) {
                            Text(
                                append.error.message
                                    ?: stringResource(R.string.catalog_retry),
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun ProductCard(product: ProductDto, currency: String, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            ProductImage(
                url = product.images.firstOrNull(),
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = PriceFormatter.format(product.price, currency),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    product.originalPrice?.takeIf { it > product.price }?.let { old ->
                        Text(
                            text = PriceFormatter.format(old, currency),
                            style = MaterialTheme.typography.labelLarge,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
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
