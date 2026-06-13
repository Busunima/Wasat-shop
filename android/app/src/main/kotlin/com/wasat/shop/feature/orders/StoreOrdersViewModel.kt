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

/** Пресет периода по дате создания (FR-A04). */
enum class DatePreset(val days: Long?) { ALL(null), WEEK(7), MONTH(30), QUARTER(90) }

data class StoreOrdersUiState(
    val loading: Boolean = true,
    val orders: List<OrderDto> = emptyList(),
    /** Фильтр по статусу; null — все. */
    val filter: OrderStatus? = null,
    /** Период по дате создания (FR-A04). */
    val datePreset: DatePreset = DatePreset.ALL,
    /** Подстрока покупателя (email/uid). */
    val customer: String = "",
    /** Диапазон суммы в основных единицах валюты (как вводит владелец). */
    val minAmount: String = "",
    val maxAmount: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val invoice: InvoiceDoc? = null,
    /** Одноразовый CSV-экспорт (FR-A05): экран отдаёт его в share sheet. */
    val csvExport: String? = null,
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
        // Offline-first (Фаза 1b): UI читает кэш Room; список не пропадает офлайн.
        // Кэш отражает последний применённый фильтр (офлайн новый фильтр не дотянуть).
        viewModelScope.launch {
            repository.observeStoreOrders(storeId).collect { cached ->
                _uiState.update { it.copy(loading = false, orders = cached) }
            }
        }
        load()
    }

    fun load() {
        val s = _uiState.value
        val fromMs = s.datePreset.days?.let {
            System.currentTimeMillis() - it * 24L * 60 * 60 * 1000
        }
        _uiState.update { it.copy(error = null) }
        viewModelScope.launch {
            val result = repository.refreshStoreOrders(
                storeId = storeId,
                status = s.filter?.name,
                fromMs = fromMs,
                minTotal = amountToMinor(s.minAmount),
                maxTotal = amountToMinor(s.maxAmount),
                customer = s.customer.ifBlank { null },
            )
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> it.copy(loading = false)
                    is ApiResult.ApiError -> it.copy(loading = false, error = result.message)
                    is ApiResult.NetworkError ->
                        it.copy(
                            loading = false,
                            error = if (it.orders.isEmpty()) "Нет соединения с сервером" else null,
                        )
                }
            }
        }
    }

    fun onFilterChange(status: OrderStatus?) {
        _uiState.update { it.copy(filter = status) }
        load()
    }

    fun onDatePreset(preset: DatePreset) {
        _uiState.update { it.copy(datePreset = preset) }
        load()
    }

    /** Поля покупателя/суммы редактируются без перезагрузки; применяются явно. */
    fun onCustomerChange(value: String) = _uiState.update { it.copy(customer = value) }
    fun onMinAmountChange(value: String) = _uiState.update { it.copy(minAmount = value.filterAmount()) }
    fun onMaxAmountChange(value: String) = _uiState.update { it.copy(maxAmount = value.filterAmount()) }

    /** Применить введённые покупателя/сумму (кнопка «Применить»). */
    fun applyFilters() = load()

    /**
     * Основные единицы (как вводит владелец) → минорные единицы для сервера.
     * Допущение MVP: валюта с 2 знаками (×100); поле пустое/невалидное → фильтр не задан.
     */
    private fun amountToMinor(input: String): Long? =
        input.trim().replace(',', '.').toDoubleOrNull()?.let { (it * 100).toLong() }

    /** Сбросить расширенные фильтры (статус остаётся). */
    fun clearAdvancedFilters() {
        _uiState.update {
            it.copy(datePreset = DatePreset.ALL, customer = "", minAmount = "", maxAmount = "")
        }
        load()
    }

    /** CSV-экспорт заказов с текущим фильтром (FR-A05). */
    fun exportCsv() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.exportCsv(storeId, _uiState.value.filter?.name)) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(busy = false, csvExport = r.data) }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    /** Сбросить CSV после передачи в share sheet. */
    fun consumeCsv() = _uiState.update { it.copy(csvExport = null) }

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

    /** Переход статуса (валидация переходов — на сервере; UI предлагает допустимые). */
    fun setStatus(orderId: String, status: OrderStatus, trackingNo: String? = null) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.updateStatus(storeId, orderId, status.name, trackingNo)) {
                // updateStatus записал заказ в Room — список обновится через Flow
                is ApiResult.Success -> _uiState.update { it.copy(busy = false) }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }
}

/** Оставить в поле суммы только цифры и один разделитель дроби. */
private fun String.filterAmount(): String =
    filterIndexed { i, c -> c.isDigit() || ((c == '.' || c == ',') && indexOfFirst { it == '.' || it == ',' } == i) }
