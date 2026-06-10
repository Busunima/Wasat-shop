package com.wasat.shop.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.wasat.shop.core.designsystem.LocalWindowWidthSizeClass
import com.wasat.shop.core.designsystem.isExpandedLayout

/** Форма товара (FR-A02): создание и редактирование. Фото/варианты — следующим инкрементом. */
@Composable
fun ProductEditScreen(
    onSaved: () -> Unit,
    viewModel: ProductEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.save) {
        if (state.save is SaveState.Saved) onSaved()
    }

    if (state.loadingExisting) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isLoading = state.save is SaveState.Loading
    val widthModifier = if (LocalWindowWidthSizeClass.current.isExpandedLayout) {
        Modifier.widthIn(max = 480.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = widthModifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (state.productId == null) R.string.product_edit_title_new
                    else R.string.product_edit_title_edit,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.product_edit_name)) },
                isError = ProductField.NAME in state.fieldErrors,
                supportingText = { state.fieldErrors[ProductField.NAME]?.let { Text(it) } },
                enabled = !isLoading,
                singleLine = true,
            )

            OutlinedTextField(
                value = state.priceInput,
                onValueChange = viewModel::onPriceChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.product_edit_price)) },
                isError = ProductField.PRICE in state.fieldErrors,
                supportingText = { state.fieldErrors[ProductField.PRICE]?.let { Text(it) } },
                enabled = !isLoading,
                singleLine = true,
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.product_edit_description)) },
                isError = ProductField.DESCRIPTION in state.fieldErrors,
                supportingText = { state.fieldErrors[ProductField.DESCRIPTION]?.let { Text(it) } },
                enabled = !isLoading,
                minLines = 3,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.product_edit_publish),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.product_edit_publish_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(
                    checked = state.isActive,
                    onCheckedChange = viewModel::onActiveChange,
                    enabled = !isLoading,
                )
            }

            (state.save as? SaveState.Failed)?.let { failed ->
                Card {
                    Text(
                        text = failed.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            ) {
                Text(
                    stringResource(
                        if (isLoading) R.string.product_edit_saving else R.string.product_edit_save,
                    ),
                )
            }
        }
    }
}
