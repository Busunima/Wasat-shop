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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

/** Настройки магазина (FR-A01): публикация, брендинг, тема, контакты, доставка. */
@Composable
fun StoreSettingsScreen(
    onSaved: () -> Unit,
    viewModel: StoreSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.save) {
        if (state.save is SaveState.Done) onSaved()
    }

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isSaving = state.save is SaveState.Loading
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
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            // Ключевой переключатель FR-A01/FR-B01: открывает витрину посетителям
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_public),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_public_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(
                    checked = state.isPublic,
                    onCheckedChange = viewModel::onPublicChange,
                    enabled = !isSaving,
                )
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_name)) },
                isError = SettingsField.NAME in state.fieldErrors,
                supportingText = { state.fieldErrors[SettingsField.NAME]?.let { Text(it) } },
                enabled = !isSaving,
                singleLine = true,
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_description)) },
                isError = SettingsField.DESCRIPTION in state.fieldErrors,
                supportingText = { state.fieldErrors[SettingsField.DESCRIPTION]?.let { Text(it) } },
                enabled = !isSaving,
                minLines = 2,
            )

            BrandingRow(
                label = R.string.settings_logo,
                url = state.logoUrl,
                uploading = state.uploading,
                enabled = !isSaving,
                onPick = viewModel::uploadLogo,
                onRemove = viewModel::removeLogo,
            )
            BrandingRow(
                label = R.string.settings_banner,
                url = state.bannerUrl,
                uploading = state.uploading,
                enabled = !isSaving,
                onPick = viewModel::uploadBanner,
                onRemove = viewModel::removeBanner,
            )

            // Тема магазина (накладывается единым акцентом, ТЗ §11.2)
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.themePrimary,
                    onValueChange = viewModel::onThemePrimaryChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.settings_theme_primary)) },
                    isError = SettingsField.THEME in state.fieldErrors,
                    enabled = !isSaving,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.themeSecondary,
                    onValueChange = viewModel::onThemeSecondaryChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.settings_theme_secondary)) },
                    isError = SettingsField.THEME in state.fieldErrors,
                    enabled = !isSaving,
                    singleLine = true,
                )
            }
            state.fieldErrors[SettingsField.THEME]?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            // Контакты
            OutlinedTextField(
                value = state.contactEmail,
                onValueChange = viewModel::onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_contact_email)) },
                isError = SettingsField.EMAIL in state.fieldErrors,
                supportingText = { state.fieldErrors[SettingsField.EMAIL]?.let { Text(it) } },
                enabled = !isSaving,
                singleLine = true,
            )
            OutlinedTextField(
                value = state.contactPhone,
                onValueChange = viewModel::onPhoneChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_contact_phone)) },
                enabled = !isSaving,
                singleLine = true,
            )
            OutlinedTextField(
                value = state.contactAddress,
                onValueChange = viewModel::onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_contact_address)) },
                enabled = !isSaving,
                singleLine = true,
            )

            OutlinedTextField(
                value = state.deliveryCostInput,
                onValueChange = viewModel::onDeliveryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_delivery_cost)) },
                isError = SettingsField.DELIVERY in state.fieldErrors,
                supportingText = { state.fieldErrors[SettingsField.DELIVERY]?.let { Text(it) } },
                enabled = !isSaving,
                singleLine = true,
            )

            // FR-A03: порог push «низкий остаток» (пусто — дефолт сервера)
            OutlinedTextField(
                value = state.lowStockInput,
                onValueChange = viewModel::onLowStockChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_low_stock)) },
                isError = SettingsField.LOW_STOCK in state.fieldErrors,
                supportingText = {
                    Text(
                        state.fieldErrors[SettingsField.LOW_STOCK]
                            ?: stringResource(R.string.settings_low_stock_hint),
                    )
                },
                enabled = !isSaving,
                singleLine = true,
            )

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
                onClick = viewModel::saveSettings,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && !state.uploading,
            ) {
                Text(
                    stringResource(
                        if (isSaving) R.string.product_edit_saving else R.string.product_edit_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BrandingRow(
    label: Int,
    url: String,
    uploading: Boolean,
    enabled: Boolean,
    onPick: (android.net.Uri, String?) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { onPick(it, context.contentResolver.getType(it)) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        if (url.isNotBlank()) {
            ProductImage(
                url = url,
                contentDescription = stringResource(R.string.a11y_store_image),
                modifier = Modifier.size(48.dp),
            )
            TextButton(onClick = onRemove, enabled = enabled) {
                Text(stringResource(R.string.product_edit_photo_remove))
            }
        }
        OutlinedButton(
            onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            enabled = enabled && !uploading,
        ) {
            if (uploading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text(stringResource(R.string.settings_choose_image))
            }
        }
    }
}
