package com.wasat.shop.feature.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.core.network.dto.CategoryDto

/** Категории магазина (FR-A01): дерево, создание/правка/удаление. */
@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<CategoryDto?>(null) }

    pendingDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.category_delete_title)) },
            text = { Text(stringResource(R.string.category_delete_confirm, cat.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(cat.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.category_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.product_delete_cancel))
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.category_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { CategoryForm(state = state, viewModel = viewModel) }

            state.error?.let { msg ->
                item {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            when {
                state.loading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                state.categories.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.category_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                else -> items(state.categories, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        parentName = category.parentId
                            ?.let { pid -> state.categories.firstOrNull { it.id == pid }?.name },
                        busy = state.busy,
                        onEdit = { viewModel.startEdit(category) },
                        onDelete = { pendingDelete = category },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryForm(state: CategoriesUiState, viewModel: CategoriesViewModel) {
    val form = state.form
    val editing = form.editingId != null
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    if (editing) R.string.category_edit_title else R.string.category_create_title,
                ),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.category_name)) },
                isError = form.nameError != null,
                supportingText = { form.nameError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.slug,
                onValueChange = viewModel::onSlug,
                label = { Text(stringResource(R.string.category_slug)) },
                isError = form.slugError != null,
                supportingText = {
                    Text(form.slugError ?: stringResource(R.string.category_slug_hint))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.order,
                onValueChange = viewModel::onOrder,
                label = { Text(stringResource(R.string.category_order)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            // Родительская категория: «Корень» + остальные (кроме редактируемой)
            Text(
                text = stringResource(R.string.category_parent),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = form.parentId == null,
                    onClick = { viewModel.onParent(null) },
                    label = { Text(stringResource(R.string.category_parent_root)) },
                )
                state.categories
                    .filter { it.id != form.editingId }
                    .forEach { candidate ->
                        FilterChip(
                            selected = form.parentId == candidate.id,
                            onClick = { viewModel.onParent(candidate.id) },
                            label = { Text(candidate.name) },
                        )
                    }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            if (editing) R.string.product_edit_save else R.string.category_create,
                        ),
                    )
                }
                if (editing) {
                    OutlinedButton(onClick = viewModel::cancelEdit, enabled = !state.busy) {
                        Text(stringResource(R.string.product_delete_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: CategoryDto,
    parentName: String?,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = parentName?.let { "$it · ${category.slug}" } ?: category.slug,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(onClick = onEdit, enabled = !busy) {
                Text(stringResource(R.string.category_edit_action))
            }
            TextButton(onClick = onDelete, enabled = !busy) {
                Text(stringResource(R.string.category_delete))
            }
        }
    }
}
