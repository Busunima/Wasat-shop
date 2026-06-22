package com.wasat.shop.feature.orders

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.core.network.dto.ReturnDto
import com.wasat.shop.core.util.PriceFormatter

/** Очередь возвратов магазина (FR-A11): статусы и действия по жизненному циклу. */
@Composable
fun StoreReturnsScreen(viewModel: StoreReturnsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.returns_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )
        state.error?.let { msg ->
            Text(text = msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
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
            if (state.returns.isEmpty()) {
                item {
                    Text(stringResource(R.string.returns_empty), style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(state.returns, key = { it.id }) { ret ->
                ReturnCard(ret, viewModel.currency, state.busy, viewModel)
            }
        }
    }
}

@Composable
private fun ReturnCard(
    ret: ReturnDto,
    currency: String,
    busy: Boolean,
    viewModel: StoreReturnsViewModel,
) {
    val status = ReturnStatuses.parse(ret.status)
    var rejecting by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }

    if (rejecting) {
        AlertDialog(
            onDismissRequest = { rejecting = false },
            title = { Text(stringResource(R.string.return_reject_reason_title)) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.return_reject_reason_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rejecting = false
                        viewModel.reject(ret.id, reason.trim().ifEmpty { null })
                    },
                ) {
                    Text(stringResource(R.string.return_reject))
                }
            },
            dismissButton = {
                TextButton(onClick = { rejecting = false }) {
                    Text(stringResource(R.string.product_delete_cancel))
                }
            },
        )
    }

    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.return_for_order, ret.orderId.take(8)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = status?.let { stringResource(returnStatusLabelRes(it)) } ?: ret.status,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(text = ret.reason, style = MaterialTheme.typography.bodySmall)
            if (ret.refundAmount > 0) {
                Text(
                    text = stringResource(
                        R.string.return_refund_amount,
                        PriceFormatter.format(ret.refundAmount, currency),
                    ) + if (ret.refundDeferred) " · ${stringResource(R.string.return_refund_deferred)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Действия по статусу
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (status) {
                    ReturnStatus.REQUESTED -> {
                        TextButton(onClick = { viewModel.approve(ret.id) }, enabled = !busy) {
                            Text(stringResource(R.string.return_approve))
                        }
                        TextButton(onClick = { rejecting = true }, enabled = !busy) {
                            Text(stringResource(R.string.return_reject))
                        }
                    }
                    ReturnStatus.APPROVED -> TextButton(onClick = { viewModel.receive(ret.id) }, enabled = !busy) {
                        Text(stringResource(R.string.return_receive))
                    }
                    ReturnStatus.RECEIVED -> TextButton(onClick = { viewModel.refund(ret.id) }, enabled = !busy) {
                        Text(stringResource(R.string.return_refund))
                    }
                    else -> {}
                }
            }
        }
    }
}

private fun returnStatusLabelRes(status: ReturnStatus): Int = when (status) {
    ReturnStatus.REQUESTED -> R.string.return_status_requested
    ReturnStatus.APPROVED -> R.string.return_status_approved
    ReturnStatus.REJECTED -> R.string.return_status_rejected
    ReturnStatus.RECEIVED -> R.string.return_status_received
    ReturnStatus.REFUNDED -> R.string.return_status_refunded
}
