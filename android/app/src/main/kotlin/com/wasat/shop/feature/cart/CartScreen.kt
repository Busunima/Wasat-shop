package com.wasat.shop.feature.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.core.db.CartItemEntity
import com.wasat.shop.core.designsystem.ProductImage
import com.wasat.shop.core.util.PriceFormatter

/** Корзина (FR-B04): локальная, офлайн-first; оформление — FR-B05. */
@Composable
fun CartScreen(
    currency: String,
    onCheckout: () -> Unit = {},
    onMyOrders: () -> Unit = {},
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    if (state.items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.cart_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = onMyOrders) {
                    Text(stringResource(R.string.cart_my_orders))
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.cart_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                state.items,
                key = { "${it.productId}|${it.variantKey}" },
            ) { item ->
                CartItemRow(
                    item = item,
                    currency = currency,
                    onIncrement = { viewModel.increment(item) },
                    onDecrement = { viewModel.decrement(item) },
                    onRemove = { viewModel.remove(item) },
                )
            }
        }

        HorizontalDivider()
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.cart_subtotal),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = PriceFormatter.format(state.subtotal, currency),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = onCheckout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cart_checkout))
            }
            TextButton(onClick = onMyOrders, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cart_my_orders))
            }
        }
    }
}

@Composable
private fun CartItemRow(
    item: CartItemEntity,
    currency: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductImage(
                url = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.size(56.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.variantKey.isNotEmpty()) {
                    Text(
                        text = CartTotals.variantLabel(item.variantKey),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Text(
                    text = PriceFormatter.format(item.price * item.quantity, currency),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDecrement) { Text("−") }
                Text(text = "${item.quantity}", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onIncrement) { Text("+") }
            }
            OutlinedButton(onClick = onRemove) {
                Text(stringResource(R.string.cart_remove))
            }
        }
    }
}
