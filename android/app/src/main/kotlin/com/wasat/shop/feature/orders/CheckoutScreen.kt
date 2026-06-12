package com.wasat.shop.feature.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.wasat.shop.core.util.PriceFormatter
import com.wasat.shop.feature.cart.CartTotals

private val METHODS = listOf("pickup", "courier")

/** Чекаут (FR-B05): состав, промокод, доставка, итог → POST /api/checkout. */
@Composable
fun CheckoutScreen(
    onPlaced: (orderId: String) -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val items by viewModel.items.collectAsState()
    val currency = viewModel.currency

    state.placedOrder?.let { order ->
        LaunchedEffect(order.id) { onPlaced(order.id) }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.checkout_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        // Состав заказа
        Card {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = buildString {
                                append(item.name)
                                val label = CartTotals.variantLabel(item.variantKey)
                                if (label.isNotEmpty()) append(" · $label")
                                append(" × ${item.quantity}")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = PriceFormatter.format(item.price * item.quantity, currency),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // Промокод (серверный предпросмотр FR-A06)
        if (state.promo == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.promoInput,
                    onValueChange = viewModel::onPromoInput,
                    label = { Text(stringResource(R.string.checkout_promo)) },
                    isError = state.promoError != null,
                    supportingText = { state.promoError?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::applyPromo, enabled = !state.busy) {
                    Text(stringResource(R.string.checkout_promo_apply))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.checkout_promo_applied, state.promo!!.code),
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = viewModel::clearPromo) {
                    Text(stringResource(R.string.checkout_promo_remove))
                }
            }
        }

        // Доставка
        Text(
            text = stringResource(R.string.checkout_delivery),
            style = MaterialTheme.typography.titleMedium,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            METHODS.forEachIndexed { index, method ->
                SegmentedButton(
                    selected = state.method == method,
                    onClick = { viewModel.onMethodChange(method) },
                    shape = SegmentedButtonDefaults.itemShape(index, METHODS.size),
                ) {
                    Text(
                        stringResource(
                            if (method == "pickup") R.string.checkout_pickup
                            else R.string.checkout_courier,
                        ),
                    )
                }
            }
        }
        if (state.method == "courier") {
            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::onAddressChange,
                label = { Text(stringResource(R.string.checkout_address)) },
                isError = state.addressError != null,
                supportingText = { state.addressError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            // FR-B11: адресная книга — быстрый выбор сохранённого адреса
            if (state.savedAddresses.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.checkout_saved_addresses),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.savedAddresses.forEach { saved ->
                    AssistChip(
                        onClick = { viewModel.onPickAddress(saved) },
                        label = { Text(saved, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.saveAddress,
                    onCheckedChange = viewModel::onSaveAddressChange,
                )
                Text(
                    text = stringResource(R.string.checkout_save_address),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        HorizontalDivider()

        // Итог (отображение; сервер пересчитает заново)
        SummaryRow(stringResource(R.string.checkout_subtotal), viewModel.subtotal(), currency)
        state.promo?.let {
            SummaryRow(stringResource(R.string.checkout_discount), -it.discount, currency)
        }
        if (state.method == "courier" && state.promo?.freeShipping != true) {
            SummaryRow(
                stringResource(R.string.checkout_delivery_cost),
                state.deliveryCost ?: 0,
                currency,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.checkout_total),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = PriceFormatter.format(viewModel.displayTotal(state), currency),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        state.error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = viewModel::placeOrder,
            enabled = !state.busy && items.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (state.busy) R.string.checkout_placing else R.string.checkout_place,
                ),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, amount: Long, currency: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(PriceFormatter.format(amount, currency), style = MaterialTheme.typography.bodyLarge)
    }
}
