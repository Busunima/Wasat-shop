package com.wasat.shop.feature.admin

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.ContactDto
import com.wasat.shop.core.network.dto.StoreUpdateRequest
import com.wasat.shop.core.network.dto.ThemeDto
import com.wasat.shop.core.network.safeApiCall
import com.wasat.shop.core.util.PriceParser
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.util.Currency
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

enum class SettingsField { NAME, DESCRIPTION, EMAIL, DELIVERY, THEME, LOW_STOCK }

/** Валидация настроек (зеркало storeUpdateSchema). Pure JVM — под unit-тестом. */
object SettingsValidation {
    private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun validateHex(value: String): String? = when {
        value.isBlank() -> null // тема не задаётся
        !HEX.matches(value) -> "Цвет в формате #RRGGBB"
        else -> null
    }

    fun validateEmail(value: String): String? = when {
        value.isBlank() -> null
        !EMAIL.matches(value) -> "Некорректный email"
        else -> null
    }
}

data class StoreSettingsUiState(
    val loading: Boolean = true,
    val name: String = "",
    val description: String = "",
    val isPublic: Boolean = false,
    val logoUrl: String = "",
    val bannerUrl: String = "",
    val themePrimary: String = "",
    val themeSecondary: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val contactAddress: String = "",
    val deliveryCostInput: String = "",
    /** Порог push «низкий остаток» (FR-A03); пусто — дефолт сервера. */
    val lowStockInput: String = "",
    val uploading: Boolean = false,
    val fieldErrors: Map<SettingsField, String> = emptyMap(),
    val save: SaveState = SaveState.Idle,
)

/** Настройки магазина (FR-A01): публикация витрины, брендинг, тема, контакты, доставка. */
@HiltViewModel
class StoreSettingsViewModel @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
    private val imageRepository: StoreImageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])
    private val currency: String = savedStateHandle["currency"] ?: "USD"

    private val _uiState = MutableStateFlow(StoreSettingsUiState())
    val uiState: StateFlow<StoreSettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = safeApiCall(json) { api.getStore(storeId) }) {
                is ApiResult.Success -> _uiState.update {
                    val store = result.data
                    it.copy(
                        loading = false,
                        name = store.name,
                        description = store.description,
                        isPublic = store.isPublic,
                        logoUrl = store.logoUrl.orEmpty(),
                        bannerUrl = store.bannerUrl.orEmpty(),
                        themePrimary = store.theme?.primary.orEmpty(),
                        themeSecondary = store.theme?.secondary.orEmpty(),
                        contactEmail = store.contact?.email.orEmpty(),
                        contactPhone = store.contact?.phone.orEmpty(),
                        contactAddress = store.contact?.address.orEmpty(),
                        deliveryCostInput = store.deliveryCost?.let(::minorToInput) ?: "",
                        lowStockInput = store.lowStockThreshold?.toString() ?: "",
                    )
                }
                else -> _uiState.update {
                    it.copy(loading = false, save = SaveState.Failed("Не удалось загрузить настройки"))
                }
            }
        }
    }

    private fun minorToInput(minor: Long): String {
        val digits = runCatching { Currency.getInstance(currency).defaultFractionDigits }
            .getOrNull() ?: 2
        return BigDecimal.valueOf(minor).movePointLeft(digits).toPlainString()
    }

    fun onNameChange(v: String) = update { copy(name = v, fieldErrors = fieldErrors - SettingsField.NAME) }
    fun onDescriptionChange(v: String) =
        update { copy(description = v, fieldErrors = fieldErrors - SettingsField.DESCRIPTION) }
    fun onPublicChange(v: Boolean) = update { copy(isPublic = v) }
    fun onThemePrimaryChange(v: String) =
        update { copy(themePrimary = v, fieldErrors = fieldErrors - SettingsField.THEME) }
    fun onThemeSecondaryChange(v: String) =
        update { copy(themeSecondary = v, fieldErrors = fieldErrors - SettingsField.THEME) }
    fun onEmailChange(v: String) =
        update { copy(contactEmail = v, fieldErrors = fieldErrors - SettingsField.EMAIL) }
    fun onPhoneChange(v: String) = update { copy(contactPhone = v) }
    fun onAddressChange(v: String) = update { copy(contactAddress = v) }
    fun onDeliveryChange(v: String) =
        update { copy(deliveryCostInput = v, fieldErrors = fieldErrors - SettingsField.DELIVERY) }
    fun onLowStockChange(v: String) =
        update { copy(lowStockInput = v, fieldErrors = fieldErrors - SettingsField.LOW_STOCK) }
    fun removeLogo() = update { copy(logoUrl = "") }
    fun removeBanner() = update { copy(bannerUrl = "") }

    private inline fun update(block: StoreSettingsUiState.() -> StoreSettingsUiState) =
        _uiState.update(block)

    fun uploadLogo(uri: Uri, contentType: String?) = uploadBranding(uri, contentType, isLogo = true)
    fun uploadBanner(uri: Uri, contentType: String?) = uploadBranding(uri, contentType, isLogo = false)

    private fun uploadBranding(uri: Uri, contentType: String?, isLogo: Boolean) {
        if (_uiState.value.uploading) return
        _uiState.update { it.copy(uploading = true) }
        viewModelScope.launch {
            imageRepository.uploadBrandingImage(storeId, uri, contentType)
                .onSuccess { url ->
                    _uiState.update {
                        if (isLogo) it.copy(uploading = false, logoUrl = url)
                        else it.copy(uploading = false, bannerUrl = url)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(uploading = false, save = SaveState.Failed(e.message ?: "Не удалось загрузить фото"))
                    }
                }
        }
    }

    fun saveSettings() {
        val s = _uiState.value
        if (s.save is SaveState.Loading || s.loading || s.uploading) return

        val themeSet = s.themePrimary.isNotBlank() || s.themeSecondary.isNotBlank()
        val errors = buildMap {
            if (s.name.isBlank() || s.name.length > 120) put(SettingsField.NAME, "Название 1–120 символов")
            if (s.description.length > 2000) put(SettingsField.DESCRIPTION, "Описание до 2000 символов")
            SettingsValidation.validateEmail(s.contactEmail)?.let { put(SettingsField.EMAIL, it) }
            if (s.deliveryCostInput.isNotBlank() &&
                PriceParser.parse(s.deliveryCostInput, currency) == null
            ) {
                put(SettingsField.DELIVERY, "Некорректная сумма")
            }
            if (s.lowStockInput.isNotBlank() &&
                (s.lowStockInput.toLongOrNull() == null || s.lowStockInput.toLong() !in 0..10000)
            ) {
                put(SettingsField.LOW_STOCK, "Целое число 0–10000")
            }
            if (themeSet) {
                val err = SettingsValidation.validateHex(s.themePrimary.ifBlank { "x" })
                    ?: SettingsValidation.validateHex(s.themeSecondary.ifBlank { "x" })
                err?.let { put(SettingsField.THEME, "Оба цвета в формате #RRGGBB") }
            }
        }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }

        val body = StoreUpdateRequest(
            name = s.name.trim(),
            description = s.description.trim(),
            isPublic = s.isPublic,
            logoUrl = s.logoUrl,
            bannerUrl = s.bannerUrl,
            contact = ContactDto(
                email = s.contactEmail.trim(),
                phone = s.contactPhone.trim(),
                address = s.contactAddress.trim(),
            ),
            deliveryCost = s.deliveryCostInput.takeIf { it.isNotBlank() }
                ?.let { PriceParser.parse(it, currency) },
            lowStockThreshold = s.lowStockInput.takeIf { it.isNotBlank() }?.toLongOrNull(),
            theme = if (themeSet) ThemeDto(s.themePrimary.trim(), s.themeSecondary.trim()) else null,
        )

        _uiState.update { it.copy(save = SaveState.Loading) }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    save = when (val result = safeApiCall(json) { api.updateStore(storeId, body) }) {
                        is ApiResult.Success -> SaveState.Done
                        is ApiResult.ApiError -> SaveState.Failed(result.message)
                        is ApiResult.NetworkError -> SaveState.Failed("Нет соединения с сервером")
                    },
                )
            }
        }
    }
}
