package com.wasat.shop.feature.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.db.CartItemEntity
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.OrderDto
import com.wasat.shop.feature.cart.CartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyOrdersUiState(
    val loading: Boolean = true,
    val orders: List<OrderDto> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val invoice: InvoiceDoc? = null,
    /** Одноразовый флаг (FR-B11): позиции добавлены в корзину — открыть её. */
    val reordered: Boolean = false,
)

/** История заказов покупателя (FR-B06) + отмена до отгрузки. */
@HiltViewModel
class MyOrdersViewModel @Inject constructor(
    private val repository: OrdersRepository,
    private val cartRepository: CartRepository,
    ordersLive: OrdersLiveRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])
    val currency: String = savedStateHandle["currency"] ?: "USD"

    private val _uiState = MutableStateFlow(MyOrdersUiState())
    val uiState: StateFlow<MyOrdersUiState> = _uiState.asStateFlow()

    init {
        // Offline-first (Фаза 1): UI всегда читает кэш Room; список не пропадает офлайн.
        viewModelScope.launch {
            repository.observeMyOrders(storeId).collect { cached ->
                _uiState.update { it.copy(loading = false, orders = cached) }
            }
        }
        load()
        // FR-B06: live-обновление — каждый снапшот своих заказов перечитывает кэш
        viewModelScope.launch {
            ordersLive.observeMyOrders(storeId).collect { refresh() }
        }
    }

    /** Тихое обновление кэша (без спиннера) по сигналу live-листенера. */
    private suspend fun refresh() {
        repository.refreshMyOrders(storeId) // список UI обновится через Flow из Room
    }

    fun load() {
        // НЕ ставим loading=true: экран показывает кэш сразу (offline-first),
        // спиннер на весь экран скрыл бы уже загруженный список. Сеть обновляет молча.
        _uiState.update { it.copy(error = null) }
        viewModelScope.launch {
            _uiState.update {
                when (val r = repository.refreshMyOrders(storeId)) {
                    is ApiResult.Success -> it.copy(loading = false)
                    is ApiResult.ApiError -> it.copy(loading = false, error = r.message)
                    is ApiResult.NetworkError ->
                        it.copy(
                            loading = false,
                            // офлайн: есть кэш — молча показываем его, иначе ошибка
                            error = if (it.orders.isEmpty()) "Нет соединения с сервером" else null,
                        )
                }
            }
        }
    }

    /** Повторный заказ (FR-B11): позиции заказа → локальная корзина → экран корзины. */
    fun reorder(order: OrderDto) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val snapshots = order.items.map { item ->
                CartItemEntity(
                    storeId = storeId,
                    productId = item.productId,
                    variantKey = ReorderLogic.variantKeyOf(item.variant),
                    name = item.name,
                    price = item.price,
                    currency = currency,
                    imageUrl = null,
                    quantity = item.qty,
                    addedAt = System.currentTimeMillis(),
                )
            }
            cartRepository.addSnapshots(storeId, snapshots)
            _uiState.update { it.copy(busy = false, reordered = true) }
        }
    }

    /** Сбросить флаг после навигации в корзину. */
    fun consumeReorder() = _uiState.update { it.copy(reordered = false) }

    /** Загрузить HTML-инвойс заказа (FR-A04); экран печатает его в PDF. */
    fun printInvoice(orderId: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.invoiceHtml(storeId, orderId)) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(busy = false, invoice = InvoiceDoc(orderId, r.data)) }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    /** Сбросить инвойс после передачи его в системную печать. */
    fun consumeInvoice() = _uiState.update { it.copy(invoice = null) }

    fun cancel(orderId: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.cancel(storeId, orderId)) {
                // repository.cancel записал заказ в Room — список обновится через Flow
                is ApiResult.Success -> _uiState.update { it.copy(busy = false) }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }
}
