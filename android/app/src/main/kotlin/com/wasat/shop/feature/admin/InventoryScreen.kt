package com.wasat.shop.feature.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.core.network.dto.ProductDto

/** Инвентарь (FR-A03): остатки с ±, CSV-импорт, история inventoryLog. */
@Composable
fun InventoryScreen(viewModel: InventoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.use { it.readText() }
        if (!text.isNullOrBlank()) viewModel.importCsv(text)
    }

    state.importReport?.let { report ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReport,
            title = { Text(stringResource(R.string.inventory_import_report_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.inventory_import_applied, report.applied))
                    report.errors.take(10).forEach { err ->
                        Text(
                            text = "№${err.line}: ${err.raw} — ${err.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (report.errors.size > 10) {
                        Text("… и ещё ${report.errors.size - 10}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissReport) { Text("OK") }
            },
        )
    }

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.inventory_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedButton(
                onClick = { csvPicker.launch(arrayOf("text/csv", "text/plain", "text/comma-separated-values")) },
                enabled = !state.busy,
            ) {
                Text(stringResource(R.string.inventory_import_csv))
            }
        }

        state.error?.let {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                // FR-A03: повтор загрузки остатков после ошибки сети.
                TextButton(onClick = viewModel::refresh) {
                    Text(stringResource(R.string.catalog_retry))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.products, key = { it.id }) { product ->
                InventoryCard(product = product, busy = state.busy, onAdjust = viewModel::adjust)
            }

            if (state.log.isNotEmpty()) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item {
                    Text(
                        text = stringResource(R.string.inventory_log_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(state.log.size) { index ->
                    val entry = state.log[index]
                    val name = state.products.firstOrNull { it.id == entry.productId }?.name
                        ?: entry.productId.take(8)
                    Text(
                        text = buildString {
                            append(name)
                            entry.variant?.let { append(" · $it") }
                            append("  ")
                            append(if (entry.delta > 0) "+${entry.delta}" else entry.delta)
                            append("  (${entry.reason})")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryCard(
    product: ProductDto,
    busy: Boolean,
    onAdjust: (ProductDto, String?, String?, String?, Int) -> Unit,
) {
    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.inventory_total, product.totalStock),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (product.totalStock > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            if (product.variants.isEmpty()) {
                StockRow(
                    label = stringResource(R.string.inventory_no_variants),
                    stock = product.totalStock,
                    busy = busy,
                    onDelta = { delta -> onAdjust(product, null, null, null, delta) },
                )
            } else {
                product.variants.forEach { variant ->
                    StockRow(
                        label = listOfNotNull(variant.size, variant.color)
                            .joinToString(" · ")
                            .ifEmpty { variant.sku ?: "—" },
                        stock = variant.stock,
                        busy = busy,
                        onDelta = { delta ->
                            onAdjust(product, variant.sku, variant.size, variant.color, delta)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StockRow(label: String, stock: Int, busy: Boolean, onDelta: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onDelta(-1) }, enabled = !busy && stock > 0) { Text("−") }
            Text("$stock", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onDelta(+1) }, enabled = !busy) { Text("+") }
        }
    }
}
