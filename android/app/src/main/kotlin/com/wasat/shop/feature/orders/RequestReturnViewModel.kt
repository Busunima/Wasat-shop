package com.wasat.shop.feature.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.OrderItemDto
import com.wasat.shop.core.network.dto.ReturnItemDto
import com.wasat.shop.feature.analytics.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReturnLine(val item: OrderItemDto, val qty: Int)

data class RequestReturnUiState(
    val loading: Boolean = true,
    val lines: List<ReturnLine> = emptyList(),
    val reason: String = "",
    val reasonError: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

/** Заявка на возврат (FR-B09): выбор позиций (кол-во 0..заказано) + причина. */
@HiltViewModel
class RequestReturnViewModel @Inject constructor(
    private val ordersRepository: OrdersRepository,
    private val returnsRepository: ReturnsRepository,
    private val analytics: AnalyticsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])
    private val orderId: String = checkNotNull(savedStateHandle["orderId"])
    val currency: String = savedStateHandle["currency"] ?: "USD"

    private val _uiState = MutableStateFlow(RequestReturnUiState())
    val uiState: StateFlow<RequestReturnUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = ordersRepository.order(storeId, orderId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(loading = false, lines = r.data.items.map { item -> ReturnLine(item, item.qty) })
                }
                is ApiResult.ApiError -> _uiState.update { it.copy(loading = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(loading = false, error = "Нет соединения с сервером") }
            }
        }
    }

    fun setQty(index: Int, qty: Int) = _uiState.update { s ->
        s.copy(
            lines = s.lines.mapIndexed { i, line ->
                if (i == index) line.copy(qty = qty.coerceIn(0, line.item.qty)) else line
            },
        )
    }

    fun onReason(value: String) = _uiState.update { it.copy(reason = value, reasonError = null) }

    fun submit() {
        val s = _uiState.value
        if (s.busy) return
        val items = s.lines.filter { it.qty > 0 }.map { ReturnItemDto(it.item.productId, it.qty) }
        if (items.isEmpty()) {
            _uiState.update { it.copy(error = "Выберите хотя бы одну позицию") }
            return
        }
        if (s.reason.trim().length < 3) {
            _uiState.update { it.copy(reasonError = "Укажите причину возврата") }
            return
        }

        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = returnsRepository.create(storeId, orderId, items, s.reason.trim())
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> {
                        analytics.track(storeId, "return_requested") // §16
                        it.copy(busy = false, done = true)
                    }
                    is ApiResult.ApiError -> it.copy(busy = false, error = result.message)
                    is ApiResult.NetworkError -> it.copy(busy = false, error = "Нет соединения с сервером")
                }
            }
        }
    }
}
