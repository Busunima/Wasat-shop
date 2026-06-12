package com.wasat.shop.feature.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.ReturnDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoreReturnsUiState(
    val loading: Boolean = true,
    val returns: List<ReturnDto> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

/** Очередь возвратов магазина (FR-A11): решение/приём/возмещение. */
@HiltViewModel
class StoreReturnsViewModel @Inject constructor(
    private val repository: ReturnsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])
    val currency: String = savedStateHandle["currency"] ?: "USD"

    private val _uiState = MutableStateFlow(StoreReturnsUiState())
    val uiState: StateFlow<StoreReturnsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _uiState.update {
                when (val r = repository.storeReturns(storeId, null)) {
                    is ApiResult.Success -> it.copy(loading = false, returns = r.data.items)
                    is ApiResult.ApiError -> it.copy(loading = false, error = r.message)
                    is ApiResult.NetworkError ->
                        it.copy(loading = false, error = "Нет соединения с сервером")
                }
            }
        }
    }

    private fun apply(returnId: String, call: suspend () -> ApiResult<ReturnDto>) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = call()) {
                is ApiResult.Success -> _uiState.update { s ->
                    s.copy(busy = false, returns = s.returns.map { if (it.id == returnId) r.data else it })
                }
                is ApiResult.ApiError -> _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    fun approve(returnId: String) = apply(returnId) { repository.resolve(storeId, returnId, "approve") }
    fun reject(returnId: String) = apply(returnId) { repository.resolve(storeId, returnId, "reject") }
    fun receive(returnId: String) = apply(returnId) { repository.receive(storeId, returnId) }
    fun refund(returnId: String) = apply(returnId) { repository.refund(storeId, returnId) }
}
