package com.wasat.shop.feature.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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

        // Шаблоны (FR-A07): подставляют заголовок и текст.
        Text(
            text = stringResource(R.string.broadcast_templates_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TEMPLATES.forEach { tpl ->
                val title = stringResource(tpl.titleRes)
                val body = stringResource(tpl.bodyRes)
                AssistChip(
                    onClick = { viewModel.applyTemplate(title, body) },
                    label = { Text(stringResource(tpl.labelRes)) },
                )
            }
        }

        // Сегмент адресатов (FR-A07): все / с заказами / без заказов.
        Text(
            text = stringResource(R.string.broadcast_segment_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SEGMENT_OPTIONS.forEach { (value, labelRes) ->
                FilterChip(
                    selected = state.segment == value,
                    onClick = { viewModel.onSegment(value) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }

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

/** Сегменты адресатов (FR-A07) — зеркало BROADCAST_SEGMENTS сервера. */
private val SEGMENT_OPTIONS = listOf(
    "all" to R.string.broadcast_segment_all,
    "with_orders" to R.string.broadcast_segment_with_orders,
    "no_orders" to R.string.broadcast_segment_no_orders,
)

private data class BroadcastTemplate(val titleRes: Int, val bodyRes: Int, val labelRes: Int)

/** Заготовки рассылок (FR-A07, client-only) — подставляются в форму. */
private val TEMPLATES = listOf(
    BroadcastTemplate(
        R.string.broadcast_tpl_sale_title,
        R.string.broadcast_tpl_sale_body,
        R.string.broadcast_tpl_sale,
    ),
    BroadcastTemplate(
        R.string.broadcast_tpl_new_title,
        R.string.broadcast_tpl_new_body,
        R.string.broadcast_tpl_new,
    ),
    BroadcastTemplate(
        R.string.broadcast_tpl_back_title,
        R.string.broadcast_tpl_back_body,
        R.string.broadcast_tpl_back,
    ),
)
