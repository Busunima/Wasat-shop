package com.wasat.shop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.core.designsystem.LocalWindowWidthSizeClass
import com.wasat.shop.core.designsystem.WasatTheme
import com.wasat.shop.core.network.ConnectivityViewModel
import com.wasat.shop.core.sync.OutboxStatusViewModel
import com.wasat.shop.core.update.ForceUpdateViewModel
import com.wasat.shop.feature.auth.AuthRepository
import com.wasat.shop.navigation.WasatNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Единственная Activity (single-activity + Compose-навигация).
 * edge-to-edge обязателен (ТЗ §11.4, на API 36 opt-out игнорируется).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Адаптивность (ТЗ §11.5): экраны читают класс ширины через CompositionLocal.
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(
                LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass,
            ) {
                WasatTheme {
                    AppRoot(authRepository)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(authRepository: AuthRepository) {
    // Scaffold корректно обрабатывает insets системных баров (edge-to-edge).
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Force-update (§11.5): блокирующий экран, если версия ниже минимальной.
            val forceUpdateViewModel: ForceUpdateViewModel = hiltViewModel()
            val updateRequired by forceUpdateViewModel.updateRequired.collectAsState()
            if (updateRequired) {
                ForceUpdateScreen()
                return@Column
            }
            val connectivityViewModel: ConnectivityViewModel = hiltViewModel()
            val online by connectivityViewModel.online.collectAsState()
            val outboxViewModel: OutboxStatusViewModel = hiltViewModel()
            val pending by outboxViewModel.pending.collectAsState()
            if (!online) OfflineBanner()
            if (pending > 0) SyncBanner(pending)
            WasatNavHost(authRepository)
        }
    }
}

/** Блокирующий экран обязательного обновления (§11.5 force-update). */
@Composable
private fun ForceUpdateScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.force_update_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.force_update_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = {
            val uri = Uri.parse("market://details?id=${context.packageName}")
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }) {
            Text(stringResource(R.string.force_update_button))
        }
    }
}

/** Индикатор очереди синхронизации (Фаза 2 outbox): N мутаций ждут отправки. */
@Composable
private fun SyncBanner(pending: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.sync_pending, pending),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
    }
}

/** Глобальная плашка офлайн-режима (Фаза 0 offline-first). */
@Composable
private fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.offline_banner),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
    }
}
