package com.wasat.shop.feature.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.PromoCreateRequest
import com.wasat.shop.core.network.dto.PromoDto
import com.wasat.shop.core.network.safeApiCall
import com.wasat.shop.core.util.PriceParser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Форма создания промокода (черновик ввода + ошибки полей). */
data class PromoForm(
    val code: String = "",
    val type: String = "percent",
    val value: String = "",
    val minAmount: String = "",
    val usageLimit: String = "",
    val codeError: String? = null,
    val valueError: String? = null,
    val minAmountError: String? = null,
    val usageLimitError: String? = null,
)

data class PromocodesUiState(
    val loading: Boolean = true,
    val promos: List<PromoDto> = emptyList(),
    val form: PromoForm = PromoForm(),
    val busy: Boolean = false,
    val error: String? = null,
)

/** Управление промокодами магазина (FR-A06): список, создание, удаление. */
@HiltViewModel
class PromocodesViewModel @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])
    val currency: String = savedStateHandle["currency"] ?: "USD"

    private val _uiState = MutableStateFlow(PromocodesUiState())
    val uiState: StateFlow<PromocodesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _uiState.update {
                when (val r = safeApiCall(json) { api.listPromocodes(storeId) }) {
                    is ApiResult.Success -> it.copy(loading = false, promos = r.data.items)
                    is ApiResult.ApiError -> it.copy(loading = false, error = r.message)
                    is ApiResult.NetworkError ->
                        it.copy(loading = false, error = "Нет соединения с сервером")
                }
            }
        }
    }

    fun onCode(v: String) = _uiState.update { it.copy(form = it.form.copy(code = v, codeError = null)) }
    fun onType(v: String) = _uiState.update { it.copy(form = it.form.copy(type = v, valueError = null)) }
    fun onValue(v: String) = _uiState.update { it.copy(form = it.form.copy(value = v, valueError = null)) }
    fun onMinAmount(v: String) =
        _uiState.update { it.copy(form = it.form.copy(minAmount = v, minAmountError = null)) }
    fun onUsageLimit(v: String) =
        _uiState.update { it.copy(form = it.form.copy(usageLimit = v, usageLimitError = null)) }

    /** Клиентская валидация (зеркало схемы) → POST → перезагрузка списка. */
    fun create() {
        val form = _uiState.value.form
        val codeError = PromoFormValidation.validateCode(form.code)
        val valueError = PromoFormValidation.validateValue(form.type, form.value, currency)
        val minError = PromoFormValidation.validateMinAmount(form.minAmount, currency)
        val limitError = PromoFormValidation.validateUsageLimit(form.usageLimit)
        if (codeError != null || valueError != null || minError != null || limitError != null) {
            _uiState.update {
                it.copy(
                    form = form.copy(
                        codeError = codeError,
                        valueError = valueError,
                        minAmountError = minError,
                        usageLimitError = limitError,
                    ),
                )
            }
            return
        }
        if (_uiState.value.busy) return

        val request = PromoCreateRequest(
            code = PromoFormValidation.normalizeCode(form.code),
            type = form.type,
            value = valueToMinor(form.type, form.value),
            minAmount = if (form.minAmount.isBlank()) 0 else minorOf(form.minAmount),
            usageLimit = form.usageLimit.trim().toIntOrNull(),
        )

        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = safeApiCall(json) { api.createPromocode(storeId, request) }) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(busy = false, form = PromoForm()) }
                    load()
                }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    fun delete(code: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = safeApiCall(json) { api.deletePromocode(storeId, code) }) {
                is ApiResult.Success -> {
                    _uiState.update { s -> s.copy(busy = false, promos = s.promos.filter { it.code != code }) }
                }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    /** percent → процент как есть; fixed → минорные единицы; free_shipping → 0. */
    private fun valueToMinor(type: String, input: String): Int = when (type) {
        "percent" -> input.trim().toIntOrNull() ?: 0
        "free_shipping" -> 0
        else -> minorOf(input)
    }

    private fun minorOf(input: String): Int =
        (PriceParser.parse(input, currency) ?: 0L).toInt()
}
