package com.wasat.shop.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R

/** Главный экран: карточка своего магазина + вход в каталог. Витрина покупателя — Фаза 2. */
@Composable
fun HomeScreen(
    slug: String?,
    onOpenCatalog: (storeId: String, currency: String) -> Unit,
    onOpenMyProducts: (storeId: String, currency: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
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
                Button(onClick = { onOpenCatalog(s.storeId, s.currency) }) {
                    Text(stringResource(R.string.home_open_catalog))
                }
                OutlinedButton(onClick = { onOpenMyProducts(s.storeId, s.currency) }) {
                    Text(stringResource(R.string.home_my_products))
                }
            }
        }
    }
}
