package com.wasat.shop.feature.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
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

/** Форма заявки на возврат (FR-B09): кол-во по позициям + причина. */
@Composable
fun RequestReturnScreen(
    onDone: () -> Unit,
    viewModel: RequestReturnViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    if (state.done) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.return_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        state.lines.forEachIndexed { index, line ->
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = line.item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { viewModel.setQty(index, line.qty - 1) },
                            enabled = line.qty > 0,
                        ) { Text("−") }
                        Text("${line.qty} / ${line.item.qty}", style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = { viewModel.setQty(index, line.qty + 1) },
                            enabled = line.qty < line.item.qty,
                        ) { Text("+") }
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.reason,
            onValueChange = viewModel::onReason,
            label = { Text(stringResource(R.string.return_reason)) },
            isError = state.reasonError != null,
            supportingText = { state.reasonError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        state.error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = viewModel::submit,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(if (state.busy) R.string.return_submitting else R.string.return_submit),
            )
        }
    }
}
