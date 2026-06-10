package com.wasat.shop.feature.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.util.PriceFormatter

/** «Мои товары» (FR-A02): все статусы, создание и редактирование. */
@Composable
fun MyProductsScreen(
    currency: String,
    onAddProduct: () -> Unit,
    onEditProduct: (productId: String) -> Unit,
    viewModel: MyProductsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Перезапускается при каждом входе в композицию — включая возврат из формы товара.
    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.my_products_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Button(onClick = onAddProduct) {
                Text(stringResource(R.string.my_products_add))
            }
        }

        when (val s = state) {
            MyProductsUiState.Loading -> Centered { CircularProgressIndicator() }

            is MyProductsUiState.Error -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = viewModel::load) {
                        Text(stringResource(R.string.catalog_retry))
                    }
                }
            }

            is MyProductsUiState.Loaded -> if (s.products.isEmpty()) {
                Centered {
                    Text(
                        text = stringResource(R.string.my_products_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.products, key = { it.id }) { product ->
                        AdminProductRow(
                            product = product,
                            currency = currency,
                            onClick = { onEditProduct(product.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminProductRow(product: ProductDto, currency: String, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = PriceFormatter.format(product.price, currency),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            AssistChip(
                onClick = onClick,
                label = {
                    Text(
                        stringResource(
                            when (product.status) {
                                "active" -> R.string.product_status_active
                                "archived" -> R.string.product_status_archived
                                else -> R.string.product_status_draft
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}
