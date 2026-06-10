package com.wasat.shop.feature.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.ImportReportDto
import com.wasat.shop.core.network.dto.InventoryLogEntryDto
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.StockAdjustRequest
import com.wasat.shop.core.network.dto.VariantSelectorDto
import com.wasat.shop.core.network.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class InventoryUiState(
    val loading: Boolean = true,
    val products: List<ProductDto> = emptyList(),
    val log: List<InventoryLogEntryDto> = emptyList(),
    val busy: Boolean = false,
    val importReport: ImportReportDto? = null,
    val error: String? = null,
)

/** Инвентарь (FR-A03): корректировка остатков ± , CSV-импорт, история. */
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
    private val adminProducts: AdminProductsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val products = adminProducts.listProducts(storeId)
            val log = safeApiCall(json) { api.inventoryLog(storeId, mapOf("limit" to "30")) }
            _uiState.update {
                it.copy(
                    loading = false,
                    products = (products as? ApiResult.Success)?.data?.items ?: it.products,
                    log = (log as? ApiResult.Success)?.data?.items ?: it.log,
                    error = if (products is ApiResult.Success) null else "Не удалось загрузить инвентарь",
                )
            }
        }
    }

    /** Дельта-корректировка остатка варианта (или товара без вариантов). */
    fun adjust(product: ProductDto, variantSku: String?, size: String?, color: String?, delta: Int) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val body = StockAdjustRequest(
                variant = if (product.variants.isEmpty()) {
                    null
                } else {
                    VariantSelectorDto(sku = variantSku, size = size, color = color)
                },
                delta = delta,
            )
            when (val result = safeApiCall(json) { api.adjustStock(storeId, product.id, body) }) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            busy = false,
                            products = state.products.map { p ->
                                if (p.id != product.id) p
                                else p.copy(
                                    totalStock = result.data.totalStock,
                                    variants = result.data.variants,
                                )
                            },
                        )
                    }
                    refreshLog()
                }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = result.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    /** CSV-импорт «sku,stock» (абсолютные значения остатков). */
    fun importCsv(csv: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null, importReport = null) }
        viewModelScope.launch {
            val body = csv.toRequestBody("text/csv".toMediaType())
            when (val result = safeApiCall(json) { api.importStockCsv(storeId, body) }) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(busy = false, importReport = result.data) }
                    refresh()
                }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = result.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    fun dismissReport() = _uiState.update { it.copy(importReport = null) }

    private fun refreshLog() {
        viewModelScope.launch {
            val log = safeApiCall(json) { api.inventoryLog(storeId, mapOf("limit" to "30")) }
            if (log is ApiResult.Success) {
                _uiState.update { it.copy(log = log.data.items) }
            }
        }
    }
}
