package com.wasat.shop.feature.admin

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.core.network.dto.PromoDto
import com.wasat.shop.core.util.PriceFormatter

private val PROMO_TYPES = listOf("percent", "fixed", "free_shipping")

/** Промокоды магазина (FR-A06): создание и список с удалением. */
@Composable
fun PromocodesScreen(viewModel: PromocodesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    pendingDelete?.let { code ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.promo_delete_title)) },
            text = { Text(stringResource(R.string.promo_delete_confirm, code)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(code)
                    pendingDelete = null
                }) { Text(stringResource(R.string.promo_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.catalog_retry))
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.promo_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CreatePromoForm(state = state, viewModel = viewModel)
            }

            state.error?.let { msg ->
                item {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            when {
                state.loading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.promos.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.promo_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                else -> items(state.promos, key = { it.code }) { promo ->
                    PromoRow(
                        promo = promo,
                        currency = viewModel.currency,
                        busy = state.busy,
                        onDelete = { pendingDelete = promo.code },
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatePromoForm(
    state: PromocodesUiState,
    viewModel: PromocodesViewModel,
) {
    val form = state.form
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.promo_create_title),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = form.code,
                onValueChange = viewModel::onCode,
                label = { Text(stringResource(R.string.promo_code)) },
                isError = form.codeError != null,
                supportingText = { form.codeError?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PROMO_TYPES.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = form.type == type,
                        onClick = { viewModel.onType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, PROMO_TYPES.size),
                    ) {
                        Text(
                            stringResource(
                                when (type) {
                                    "percent" -> R.string.promo_type_percent
                                    "fixed" -> R.string.promo_type_fixed
                                    else -> R.string.promo_type_free_shipping
                                },
                            ),
                        )
                    }
                }
            }

            if (form.type != "free_shipping") {
                OutlinedTextField(
                    value = form.value,
                    onValueChange = viewModel::onValue,
                    label = {
                        Text(
                            stringResource(
                                if (form.type == "percent") R.string.promo_value_percent
                                else R.string.promo_value_fixed,
                            ),
                        )
                    },
                    isError = form.valueError != null,
                    supportingText = { form.valueError?.let { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = form.minAmount,
                onValueChange = viewModel::onMinAmount,
                label = { Text(stringResource(R.string.promo_min_amount)) },
                isError = form.minAmountError != null,
                supportingText = { form.minAmountError?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.usageLimit,
                onValueChange = viewModel::onUsageLimit,
                label = { Text(stringResource(R.string.promo_usage_limit)) },
                isError = form.usageLimitError != null,
                supportingText = { form.usageLimitError?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::create,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.promo_create))
            }
        }
    }
}

@Composable
private fun PromoRow(promo: PromoDto, currency: String, busy: Boolean, onDelete: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(promo.code, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = promoSummary(promo, currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (!promo.active) {
                    Text(
                        text = stringResource(R.string.promo_inactive),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = onDelete, enabled = !busy) {
                Text(stringResource(R.string.promo_delete))
            }
        }
    }
}

@Composable
private fun promoSummary(promo: PromoDto, currency: String): String {
    val effect = when (promo.type) {
        "percent" -> "−${promo.value}%"
        "free_shipping" -> stringResource(R.string.promo_type_free_shipping)
        else -> "−${PriceFormatter.format(promo.value.toLong(), currency)}"
    }
    val limit = promo.usageLimit?.let { " · ${promo.usedCount}/$it" } ?: ""
    return effect + limit
}
