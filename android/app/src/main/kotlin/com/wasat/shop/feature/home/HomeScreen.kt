package com.wasat.shop.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wasat.shop.R

/** Плейсхолдер главного экрана — наполняется в Фазе 2 («Витрина»). */
@Composable
fun HomeScreen(slug: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (slug != null) {
                stringResource(R.string.home_store_created, slug)
            } else {
                stringResource(R.string.app_name)
            },
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}
