package com.wasat.shop.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onOpenOrders: (storeId: String, currency: String) -> Unit,
    onOpenReturns: (storeId: String, currency: String) -> Unit,
    onOpenSettings: (storeId: String, currency: String) -> Unit,
    onOpenInventory: (storeId: String) -> Unit,
    onOpenCategories: (storeId: String) -> Unit,
    onOpenPromocodes: (storeId: String, currency: String) -> Unit,
    onOpenStaff: (storeId: String) -> Unit,
    onOpenBroadcast: (storeId: String) -> Unit,
    onOpenAnalytics: (storeId: String, currency: String) -> Unit,
    onOpenStore: (slug: String) -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    notificationsViewModel: com.wasat.shop.feature.notifications.NotificationCenterViewModel =
        hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val lastStore by viewModel.lastStore.collectAsState()
    val planUsage by viewModel.planUsage.collectAsState()
    val unreadNotifications by notificationsViewModel.unread.collectAsState()
    val context = LocalContext.current

    // FR-B10: разрешение на уведомления (Android 13+) — запрашиваем один раз на Home
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* отказ не блокирует UX — push просто не показываются */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
                planUsage?.let { PlanCard(it) }
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
                    onClick = { onOpenOrders(s.storeId, s.currency) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_orders))
                }
                OutlinedButton(
                    onClick = { onOpenReturns(s.storeId, s.currency) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_returns))
                }
                OutlinedButton(
                    onClick = { onOpenInventory(s.storeId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_inventory))
                }
                OutlinedButton(
                    onClick = { onOpenCategories(s.storeId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_categories))
                }
                OutlinedButton(
                    onClick = { onOpenPromocodes(s.storeId, s.currency) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_promocodes))
                }
                OutlinedButton(
                    onClick = { onOpenAnalytics(s.storeId, s.currency) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_analytics))
                }
                OutlinedButton(
                    onClick = { onOpenStaff(s.storeId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_staff))
                }
                OutlinedButton(
                    onClick = { onOpenBroadcast(s.storeId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_broadcast))
                }
                OutlinedButton(
                    onClick = { onOpenSettings(s.storeId, s.currency) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_settings))
                }
            }
        }

        // Центр уведомлений (§11.5) — доступен всем; в подписи число непрочитанных
        OutlinedButton(
            onClick = onOpenNotifications,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (unreadNotifications > 0) {
                    stringResource(R.string.home_notifications_badge, unreadNotifications)
                } else {
                    stringResource(R.string.home_notifications)
                },
            )
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

/** Карточка тарифа и использования владельца (FR-S03). */
@Composable
private fun PlanCard(plan: com.wasat.shop.core.network.dto.PlanUsageDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_title, plan.plan),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    R.string.plan_usage_products,
                    plan.usage.products,
                    plan.limits.maxProducts?.toString() ?: "∞",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.plan_usage_staff,
                    plan.usage.staff,
                    plan.limits.maxStaff?.toString() ?: "∞",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
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
