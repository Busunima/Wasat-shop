package com.wasat.shop.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R

/** Рассылка владельца всем покупателям магазина (FR-A07): заголовок, текст, отправка. */
@Composable
fun BroadcastScreen(viewModel: BroadcastViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.broadcast_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.broadcast_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::onTitle,
            label = { Text(stringResource(R.string.broadcast_field_title)) },
            isError = state.titleError != null,
            supportingText = { state.titleError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.body,
            onValueChange = viewModel::onBody,
            label = { Text(stringResource(R.string.broadcast_field_body)) },
            isError = state.bodyError != null,
            supportingText = { state.bodyError?.let { Text(it) } },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::send,
            enabled = !state.sending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.sending) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text(stringResource(R.string.broadcast_send))
        }

        state.error?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        state.sent?.let { stats ->
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.broadcast_sent_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.broadcast_sent_stats, stats.targets, stats.success, stats.failure),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
