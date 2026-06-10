package com.wasat.shop.feature.home

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.wasat.shop.R
import com.wasat.shop.feature.storefront.QrSlugParser

/** Главный экран: свой магазин (владелец) + открытие чужой витрины по QR/ссылке (FR-B01). */
@Composable
fun HomeScreen(
    slug: String?,
    onOpenCatalog: (storeId: String, currency: String) -> Unit,
    onOpenMyProducts: (storeId: String, currency: String) -> Unit,
    onOpenSettings: (storeId: String, currency: String) -> Unit,
    onOpenInventory: (storeId: String) -> Unit,
    onOpenStore: (slug: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val lastStore by viewModel.lastStore.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            HomeUiState.Loading -> CircularProgressIndicator()

            HomeUiState.NoStore -> Text(
                text = if (slug != null) {
                    stringResource(R.string.home_store_created, slug)
                } else {
                    stringResource(R.string.app_name)
                },
                style = MaterialTheme.typography.headlineSmall,
            )

            is HomeUiState.MyStore -> {
                Text(text = s.name, style = MaterialTheme.typography.headlineMedium)
                Button(
                    onClick = { onOpenCatalog(s.storeId, s.currency) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_open_catalog))
                }
                OutlinedButton(
                    onClick = { onOpenMyProducts(s.storeId, s.currency) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_my_products))
                }
                OutlinedButton(
                    onClick = { onOpenInventory(s.storeId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_inventory))
                }
                OutlinedButton(
                    onClick = { onOpenSettings(s.storeId, s.currency) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_settings))
                }
            }
        }

        // FR-B01: открыть чужой магазин по QR — доступно всем
        OutlinedButton(
            onClick = { scanStoreQr(context, onOpenStore) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.home_scan))
        }

        // Быстрый возврат к последнему открытому магазину (FR-B01: кэш)
        lastStore?.let { last ->
            OutlinedButton(
                onClick = { onOpenStore(last.slug) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_last_store, last.name))
            }
        }
    }
}

/** Системный Google code scanner (без camera-permission); парс slug → навигация. */
private fun scanStoreQr(context: Context, onOpenStore: (slug: String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            QrSlugParser.parse(barcode.rawValue)?.let(onOpenStore)
        }
}
