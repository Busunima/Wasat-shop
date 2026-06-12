package com.wasat.shop.feature.orders

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.domain.model.OrderStatus

/** Фильтр-чипы: ходовые статусы кабинета. */
private val FILTERS = listOf(
    null,
    OrderStatus.NEW,
    OrderStatus.CONFIRMED,
    OrderStatus.PROCESSING,
    OrderStatus.SHIPPED,
    OrderStatus.DELIVERED,
)

/** Заказы магазина (FR-A04): фильтр по статусу + переходы (trackingNo для SHIPPED). */
@Composable
fun StoreOrdersScreen(viewModel: StoreOrdersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    // Заказ, для которого запрошен переход в SHIPPED (диалог trackingNo)
    var shippingOrderId by remember { mutableStateOf<String?>(null) }
    var trackingInput by remember { mutableStateOf("") }

    // FR-A04: получив HTML-инвойс, печатаем его в PDF и сбрасываем одноразовое состояние.
    LaunchedEffect(state.invoice) {
        state.invoice?.let { doc ->
            InvoicePrinter.print(context, doc.html, "invoice-${doc.orderId.take(8)}")
            viewModel.consumeInvoice()
        }
    }

    shippingOrderId?.let { orderId ->
        AlertDialog(
            onDismissRequest = { shippingOrderId = null },
            title = { Text(stringResource(R.string.order_ship_title)) },
            text = {
                OutlinedTextField(
                    value = trackingInput,
                    onValueChange = { trackingInput = it },
                    label = { Text(stringResource(R.string.order_tracking_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setStatus(
                        orderId,
                        OrderStatus.SHIPPED,
                        trackingInput.trim().ifEmpty { null },
                    )
                    shippingOrderId = null
                    trackingInput = ""
                }) { Text(stringResource(R.string.order_ship_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { shippingOrderId = null }) {
                    Text(stringResource(R.string.catalog_retry))
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.store_orders_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FILTERS.forEach { status ->
                FilterChip(
                    selected = state.filter == status,
                    onClick = { viewModel.onFilterChange(status) },
                    label = {
                        Text(
                            status?.let { stringResource(statusLabelRes(it)) }
                                ?: stringResource(R.string.store_orders_all),
                        )
                    },
                )
            }
        }

        state.error?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.orders.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.store_orders_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(state.orders, key = { it.id }) { order ->
                val status = OrderTransitions.parse(order.status)
                OrderCard(order = order, currency = viewModel.currency) {
                    if (status != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OrderTransitions.next(status).forEach { next ->
                                TextButton(
                                    onClick = {
                                        if (next == OrderStatus.SHIPPED) {
                                            shippingOrderId = order.id
                                        } else {
                                            viewModel.setStatus(order.id, next)
                                        }
                                    },
                                    enabled = !state.busy,
                                ) {
                                    Text(stringResource(statusLabelRes(next)))
                                }
                            }
                        }
                    }
                    // FR-A04: инвойс заказа (печать/сохранение в PDF)
                    TextButton(
                        onClick = { viewModel.printInvoice(order.id) },
                        enabled = !state.busy,
                    ) {
                        Text(stringResource(R.string.order_invoice))
                    }
                }
            }
        }
    }
}
