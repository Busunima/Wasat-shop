package com.wasat.shop.feature.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R

/** История заказов покупателя (FR-B06): статусы, трекинг, отмена до отгрузки. */
@Composable
fun MyOrdersScreen(viewModel: MyOrdersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.my_orders_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        state.error?.let { msg ->
            item { Text(text = msg, color = MaterialTheme.colorScheme.error) }
        }

        if (state.orders.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.my_orders_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        items(state.orders, key = { it.id }) { order ->
            val status = OrderTransitions.parse(order.status)
            OrderCard(order = order, currency = viewModel.currency) {
                if (status?.isCancellableByBuyer == true) {
                    TextButton(
                        onClick = { viewModel.cancel(order.id) },
                        enabled = !state.busy,
                    ) {
                        Text(stringResource(R.string.order_cancel))
                    }
                }
            }
        }
    }
}
