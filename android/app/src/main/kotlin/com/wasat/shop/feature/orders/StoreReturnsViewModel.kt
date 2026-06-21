package com.wasat.shop.feature.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.ReturnDto
import com.wasat.shop.core.sync.OutboxRepository
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
    private val outbox: OutboxRepository,
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

    /**
     * Переход возврата (offline-first, B5.3): оптимистично меняем кэш и кладём
     * действие в outbox — доедет при наличии сети (сервер идемпотентен).
     */
    private fun act(returnId: String, optimisticStatus: String, action: String, comment: String? = null) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repository.optimisticStoreReturnStatus(storeId, returnId, optimisticStatus)
            outbox.enqueueReturnAction(storeId, returnId, action, comment)
            _uiState.update { it.copy(busy = false) }
        }
    }

    fun approve(returnId: String) = act(returnId, "APPROVED", "approve")

    /** Отклонение возврата с необязательной причиной (FR-A11). */
    fun reject(returnId: String, comment: String? = null) = act(returnId, "REJECTED", "reject", comment)
    fun receive(returnId: String) = act(returnId, "RECEIVED", "receive")
    fun refund(returnId: String) = act(returnId, "REFUNDED", "refund")
}
