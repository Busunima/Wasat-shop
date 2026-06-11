package com.wasat.shop.feature.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R

/** Форма отзыва о товаре (FR-B08): выбор звёзд + текст. */
@Composable
fun WriteReviewScreen(
    onDone: () -> Unit,
    viewModel: WriteReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    if (state.done) {
        LaunchedEffect(Unit) { onDone() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.review_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        StarRow(
            rating = state.rating,
            starSize = 36.sp,
            onRate = viewModel::onRating,
        )

        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::onText,
            label = { Text(stringResource(R.string.review_text)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        state.error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = viewModel::submit,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (state.busy) R.string.review_submitting else R.string.review_submit,
                ),
            )
        }
    }
}
