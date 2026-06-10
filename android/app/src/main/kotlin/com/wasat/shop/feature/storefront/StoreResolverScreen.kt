package com.wasat.shop.feature.storefront

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R

/** Промежуточный экран входа в витрину по slug (deep link / QR, FR-B01). */
@Composable
fun StoreResolverScreen(
    onResolved: (storeId: String, currency: String) -> Unit,
    onBack: () -> Unit,
    viewModel: StoreResolverViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state) {
        (state as? ResolveUiState.Resolved)?.let { onResolved(it.storeId, it.currency) }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            ResolveUiState.Loading, is ResolveUiState.Resolved -> CircularProgressIndicator()

            is ResolveUiState.NotFound -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.store_not_found, s.slug),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onBack) {
                    Text(stringResource(R.string.store_not_found_back))
                }
            }

            is ResolveUiState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(s.message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = viewModel::resolve) {
                    Text(stringResource(R.string.catalog_retry))
                }
            }
        }
    }
}
