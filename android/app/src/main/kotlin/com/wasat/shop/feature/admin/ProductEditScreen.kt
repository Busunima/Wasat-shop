package com.wasat.shop.feature.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.core.designsystem.LocalWindowWidthSizeClass
import com.wasat.shop.core.designsystem.ProductImage
import com.wasat.shop.core.designsystem.isExpandedLayout

private val STATUS_OPTIONS = listOf("active", "draft", "archived")

/** Форма товара (FR-A02 полный): поля, фото (мультизагрузка), варианты, статус, удаление. */
@Composable
fun ProductEditScreen(
    onSaved: () -> Unit,
    viewModel: ProductEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.save) {
        if (state.save is SaveState.Done) onSaved()
    }

    if (state.loadingExisting) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.product_delete_title)) },
            text = { Text(stringResource(R.string.product_delete_text, state.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text(
                        stringResource(R.string.product_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.product_delete_cancel))
                }
            },
        )
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

            FormField(
                value = state.name,
                onChange = viewModel::onNameChange,
                label = R.string.product_edit_name,
                error = state.fieldErrors[ProductField.NAME],
                enabled = !isLoading,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    FormField(
                        value = state.priceInput,
                        onChange = viewModel::onPriceChange,
                        label = R.string.product_edit_price,
                        error = state.fieldErrors[ProductField.PRICE],
                        enabled = !isLoading,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    FormField(
                        value = state.originalPriceInput,
                        onChange = viewModel::onOriginalPriceChange,
                        label = R.string.product_edit_original_price,
                        error = state.fieldErrors[ProductField.ORIGINAL_PRICE],
                        enabled = !isLoading,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    FormField(
                        value = state.sku,
                        onChange = viewModel::onSkuChange,
                        label = R.string.product_edit_sku,
                        error = state.fieldErrors[ProductField.SKU],
                        enabled = !isLoading,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    FormField(
                        value = state.barcode,
                        onChange = viewModel::onBarcodeChange,
                        label = R.string.product_edit_barcode,
                        error = state.fieldErrors[ProductField.BARCODE],
                        enabled = !isLoading,
                    )
                }
            }

            FormField(
                value = state.category,
                onChange = viewModel::onCategoryChange,
                label = R.string.product_edit_category,
                error = state.fieldErrors[ProductField.CATEGORY],
                enabled = !isLoading,
            )

            OutlinedTextField(
                value = state.tagsInput,
                onValueChange = viewModel::onTagsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.product_edit_tags)) },
                isError = ProductField.TAGS in state.fieldErrors,
                supportingText = {
                    Text(
                        state.fieldErrors[ProductField.TAGS]
                            ?: stringResource(R.string.product_edit_tags_hint),
                    )
                },
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
                enabled = !isLoading && !state.aiGenerating,
                minLines = 3,
            )

            // AI-ассист (FR-A12): генерация с нуля + переписывание существующего
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = viewModel::generateDescription,
                    enabled = !isLoading && !state.aiGenerating && state.name.isNotBlank(),
                ) {
                    Text(
                        stringResource(
                            if (state.aiGenerating) R.string.product_edit_ai_generating
                            else R.string.product_edit_ai_describe,
                        ),
                    )
                }
                TextButton(
                    onClick = viewModel::rewriteDescription,
                    enabled = !isLoading && !state.aiGenerating &&
                        state.name.isNotBlank() && state.description.isNotBlank(),
                ) {
                    Text(stringResource(R.string.product_edit_ai_rewrite))
                }
            }

            PhotosSection(state = state, viewModel = viewModel, enabled = !isLoading)

            VariantsSection(state = state, viewModel = viewModel, enabled = !isLoading)

            // Статус (FR-A02): активен / черновик / архив
            Text(
                text = stringResource(R.string.product_edit_status),
                style = MaterialTheme.typography.titleMedium,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                STATUS_OPTIONS.forEachIndexed { index, status ->
                    SegmentedButton(
                        selected = state.status == status,
                        onClick = { viewModel.onStatusChange(status) },
                        shape = SegmentedButtonDefaults.itemShape(index, STATUS_OPTIONS.size),
                        enabled = !isLoading,
                    ) {
                        Text(
                            stringResource(
                                when (status) {
                                    "active" -> R.string.product_status_active
                                    "archived" -> R.string.product_status_archived
                                    else -> R.string.product_status_draft
                                },
                            ),
                        )
                    }
                }
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
                enabled = !isLoading && state.uploadingImages == 0,
            ) {
                Text(
                    stringResource(
                        if (isLoading) R.string.product_edit_saving else R.string.product_edit_save,
                    ),
                )
            }

            if (state.productId != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                ) {
                    Text(
                        stringResource(R.string.product_delete_button),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    error: String?,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        enabled = enabled,
        singleLine = true,
    )
}

/** Фото товара: мультивыбор системным photo picker, миниатюры с удалением. */
@Composable
private fun PhotosSection(
    state: ProductEditUiState,
    viewModel: ProductEditViewModel,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = ProductFormValidation.IMAGES_MAX,
        ),
    ) { uris ->
        viewModel.addImages(uris.map { it to context.contentResolver.getType(it) })
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.product_edit_photos),
            style = MaterialTheme.typography.titleMedium,
        )

        if (state.images.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.images, key = { it }) { url ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProductImage(
                            url = url,
                            contentDescription = stringResource(R.string.a11y_product_photo_generic),
                            modifier = Modifier.size(88.dp),
                        )
                        TextButton(
                            onClick = { viewModel.removeImage(url) },
                            enabled = enabled,
                        ) {
                            Text(stringResource(R.string.product_edit_photo_remove))
                        }
                    }
                }
            }
        }

        state.fieldErrors[ProductField.IMAGES]?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        OutlinedButton(
            onClick = {
                pickImages.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            enabled = enabled && state.uploadingImages == 0,
        ) {
            if (state.uploadingImages > 0) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.product_edit_photo_uploading, state.uploadingImages),
                    modifier = Modifier.padding(start = 8.dp),
                )
            } else {
                Text(stringResource(R.string.product_edit_photo_add))
            }
        }
    }
}

/** Варианты товара: размер/цвет/остаток/SKU, добавление и удаление строк. */
@Composable
private fun VariantsSection(
    state: ProductEditUiState,
    viewModel: ProductEditViewModel,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.product_edit_variants),
            style = MaterialTheme.typography.titleMedium,
        )

        state.variants.forEachIndexed { index, variant ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = variant.size,
                        onValueChange = { viewModel.onVariantChange(index, variant.copy(size = it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.product_edit_variant_size)) },
                        enabled = enabled,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = variant.color,
                        onValueChange = { viewModel.onVariantChange(index, variant.copy(color = it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.product_edit_variant_color)) },
                        enabled = enabled,
                        singleLine = true,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = variant.stockInput,
                        onValueChange = {
                            viewModel.onVariantChange(index, variant.copy(stockInput = it))
                        },
                        modifier = Modifier.weight(0.8f),
                        label = { Text(stringResource(R.string.product_edit_variant_stock)) },
                        enabled = enabled,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = variant.sku,
                        onValueChange = { viewModel.onVariantChange(index, variant.copy(sku = it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.product_edit_variant_sku)) },
                        enabled = enabled,
                        singleLine = true,
                    )
                    TextButton(onClick = { viewModel.removeVariant(index) }, enabled = enabled) {
                        Text("✕")
                    }
                }
            }
        }

        state.fieldErrors[ProductField.VARIANTS]?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        OutlinedButton(onClick = viewModel::addVariant, enabled = enabled) {
            Text(stringResource(R.string.product_edit_variant_add))
        }
    }
}
