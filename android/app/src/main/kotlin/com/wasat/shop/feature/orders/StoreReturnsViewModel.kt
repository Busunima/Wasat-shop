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
        // Offline-first (B5.3): UI читает кэш Room; очередь возвратов не пропадает офлайн.
        viewModelScope.launch {
            repository.observeStoreReturns(storeId).collect { cached ->
                _uiState.update { it.copy(loading = false, returns = cached) }
            }
        }
        load()
    }

    fun load() {
        // НЕ ставим loading=true: кэш показывается сразу (offline-first), сеть обновляет молча.
        _uiState.update { it.copy(error = null) }
        viewModelScope.launch {
            _uiState.update {
                when (val r = repository.refreshStoreReturns(storeId, null)) {
                    is ApiResult.Success -> it.copy(loading = false)
                    is ApiResult.ApiError -> it.copy(loading = false, error = r.message)
                    is ApiResult.NetworkError ->
                        it.copy(
                            loading = false,
                            error = if (it.returns.isEmpty()) "Нет соединения с сервером" else null,
                        )
                }
            }
        }
    }

    private fun apply(call: suspend () -> ApiResult<ReturnDto>) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = call()) {
                // repository персистит возврат в Room — список обновится через Flow
                is ApiResult.Success -> _uiState.update { it.copy(busy = false) }
                is ApiResult.ApiError -> _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    fun approve(returnId: String) = apply { repository.resolve(storeId, returnId, "approve") }
    fun reject(returnId: String) = apply { repository.resolve(storeId, returnId, "reject") }
    fun receive(returnId: String) = apply { repository.receive(storeId, returnId) }
    fun refund(returnId: String) = apply { repository.refund(storeId, returnId) }
}
