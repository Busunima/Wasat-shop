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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
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
    // Заказ, для которого запрошена отмена (диалог причины, FR-A04)
    var cancellingOrderId by remember { mutableStateOf<String?>(null) }
    var cancelReasonInput by remember { mutableStateOf("") }

    // FR-A04: получив HTML-инвойс, печатаем его в PDF и сбрасываем одноразовое состояние.
    LaunchedEffect(state.invoice) {
        state.invoice?.let { doc ->
            InvoicePrinter.print(context, doc.html, "invoice-${doc.orderId.take(8)}")
            viewModel.consumeInvoice()
        }
    }

    // FR-A05: получив CSV-экспорт, отдаём его в share sheet и сбрасываем состояние.
    LaunchedEffect(state.csvExport) {
        state.csvExport?.let { csv ->
            CsvShare.share(context, csv, "orders.csv")
            viewModel.consumeCsv()
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

    // FR-A04: отмена заказа с необязательной причиной.
    cancellingOrderId?.let { orderId ->
        AlertDialog(
            onDismissRequest = { cancellingOrderId = null },
            title = { Text(stringResource(R.string.order_cancel_title)) },
            text = {
                OutlinedTextField(
                    value = cancelReasonInput,
                    onValueChange = { cancelReasonInput = it },
                    label = { Text(stringResource(R.string.order_cancel_reason_hint)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setStatus(
                        orderId,
                        OrderStatus.CANCELLED,
                        reason = cancelReasonInput.trim().ifEmpty { null },
                    )
                    cancellingOrderId = null
                    cancelReasonInput = ""
                }) { Text(stringResource(R.string.order_cancel_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { cancellingOrderId = null }) {
                    Text(stringResource(R.string.product_delete_cancel))
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.store_orders_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            // FR-A05: выгрузка заказов в CSV (учитывает выбранный фильтр)
            TextButton(onClick = viewModel::exportCsv, enabled = !state.busy) {
                Text(stringResource(R.string.orders_export_csv))
            }
        }

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

        // FR-A04: фильтр по периоду создания
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DatePreset.entries.forEach { preset ->
                FilterChip(
                    selected = state.datePreset == preset,
                    onClick = { viewModel.onDatePreset(preset) },
                    label = { Text(stringResource(datePresetLabelRes(preset))) },
                )
            }
        }

        // FR-A04: фильтр по сумме и покупателю (применяется кнопкой)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.customer,
                onValueChange = viewModel::onCustomerChange,
                label = { Text(stringResource(R.string.orders_filter_customer)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.minAmount,
                    onValueChange = viewModel::onMinAmountChange,
                    label = { Text(stringResource(R.string.orders_filter_min)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.maxAmount,
                    onValueChange = viewModel::onMaxAmountChange,
                    label = { Text(stringResource(R.string.orders_filter_max)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::applyFilters, enabled = !state.busy) {
                    Text(stringResource(R.string.orders_filter_apply))
                }
                OutlinedButton(onClick = viewModel::clearAdvancedFilters, enabled = !state.busy) {
                    Text(stringResource(R.string.orders_filter_reset))
                }
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
                                        when (next) {
                                            OrderStatus.SHIPPED -> shippingOrderId = order.id
                                            OrderStatus.CANCELLED -> cancellingOrderId = order.id
                                            else -> viewModel.setStatus(order.id, next)
                                        }
                                    },
                                    enabled = !state.busy,
                                ) {
                                    Text(stringResource(statusLabelRes(next)))
                                }
                            }
                        }
                    }
                    // FR-A04: показать причину отмены, если задана.
                    order.cancelReason?.takeIf { it.isNotBlank() }?.let { reason ->
                        Text(
                            text = stringResource(R.string.order_cancel_reason, reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
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
