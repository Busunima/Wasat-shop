package com.wasat.shop.feature.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.OrderDto
import com.wasat.shop.domain.model.OrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoreOrdersUiState(
    val loading: Boolean = true,
    val orders: List<OrderDto> = emptyList(),
    /** Фильтр по статусу; null — все. */
    val filter: OrderStatus? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/** Заказы магазина (FR-A04): список с фильтром + смена статусов по переходам. */
@HiltViewModel
class StoreOrdersViewModel @Inject constructor(
    private val repository: OrdersRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])
    val currency: String = savedStateHandle["currency"] ?: "USD"

    private val _uiState = MutableStateFlow(StoreOrdersUiState())
    val uiState: StateFlow<StoreOrdersUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val filter = _uiState.value.filter
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _uiState.update {
                when (val r = repository.storeOrders(storeId, filter?.name)) {
                    is ApiResult.Success -> it.copy(loading = false, orders = r.data.items)
                    is ApiResult.ApiError -> it.copy(loading = false, error = r.message)
                    is ApiResult.NetworkError ->
                        it.copy(loading = false, error = "Нет соединения с сервером")
                }
            }
        }
    }

    fun onFilterChange(status: OrderStatus?) {
        _uiState.update { it.copy(filter = status) }
        load()
    }

    /** Переход статуса (валидация переходов — на сервере; UI предлагает допустимые). */
    fun setStatus(orderId: String, status: OrderStatus, trackingNo: String? = null) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.updateStatus(storeId, orderId, status.name, trackingNo)) {
                is ApiResult.Success -> _uiState.update { s ->
                    s.copy(
                        busy = false,
                        orders = s.orders.map { if (it.id == orderId) r.data else it },
                    )
                }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }
}
