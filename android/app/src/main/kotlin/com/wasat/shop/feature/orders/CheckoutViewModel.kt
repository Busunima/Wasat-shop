package com.wasat.shop.feature.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.db.CartItemEntity
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.ConnectivityObserver
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.CheckoutDeliveryDto
import com.wasat.shop.core.network.dto.CheckoutItemDto
import com.wasat.shop.core.network.dto.CheckoutRequest
import com.wasat.shop.core.network.dto.OrderDto
import com.wasat.shop.core.network.dto.PromoPreviewRequest
import com.wasat.shop.core.network.safeApiCall
import com.wasat.shop.feature.analytics.AnalyticsRepository
import com.wasat.shop.feature.cart.CartRepository
import com.wasat.shop.feature.cart.CartTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Применённый промокод (серверный предпросмотр FR-A06). */
data class AppliedPromo(val code: String, val discount: Long, val freeShipping: Boolean)

data class CheckoutUiState(
    val promoInput: String = "",
    val promo: AppliedPromo? = null,
    val promoError: String? = null,
    /** pickup | courier */
    val method: String = "pickup",
    val address: String = "",
    val addressError: String? = null,
    /** Адресная книга покупателя (FR-B11), свежие сверху. */
    val savedAddresses: List<String> = emptyList(),
    /** Сохранить адрес после успешного заказа. */
    val saveAddress: Boolean = true,
    /** Стоимость курьерской доставки магазина (минорные), null — не задана. */
    val deliveryCost: Long? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** Создан заказ — экран закрывается. */
    val placedOrder: OrderDto? = null,
)

/**
 * Чекаут (FR-B05): позиции из локальной корзины, промокод (серверный предпросмотр),
 * способ доставки, итог. Сервер пересчитывает всё сам — клиентские суммы только
 * для отображения. idempotencyKey фиксируется на время жизни экрана.
 */
@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val ordersRepository: OrdersRepository,
    private val cartRepository: CartRepository,
    private val addressBook: AddressBookRepository,
    private val analytics: AnalyticsRepository,
    private val connectivity: ConnectivityObserver,
    private val api: WasatApi,
    private val json: Json,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])
    val currency: String = savedStateHandle["currency"] ?: "USD"

    /** Один ключ на попытку оформления: ретраи не создадут дубль заказа (§10.1). */
    private val idempotencyKey: String = UUID.randomUUID().toString()

    val items: StateFlow<List<CartItemEntity>> = cartRepository.observeCart(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    /** Сетевая доступность: чекаут блокируется офлайн до восстановления сети (FR-B05). */
    val online: StateFlow<Boolean> = connectivity.online
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), connectivity.isOnline())

    init {
        // §16: старт оформления — числитель воронки begin_checkout
        analytics.track(storeId, "begin_checkout")
        // Стоимость доставки магазина — для отображения суммы до оформления
        viewModelScope.launch {
            val store = safeApiCall(json) { api.getStore(storeId) }
            if (store is ApiResult.Success) {
                _uiState.update { it.copy(deliveryCost = store.data.deliveryCost) }
            }
        }
        // FR-B11: адресная книга — подсказки для курьерской доставки
        viewModelScope.launch {
            val saved = addressBook.load(storeId)
            if (saved.isNotEmpty()) {
                _uiState.update { it.copy(savedAddresses = saved) }
            }
        }
    }

    fun subtotal(): Long = CartTotals.subtotal(items.value)

    /** Итог для отображения (сервер пересчитает заново). */
    fun displayTotal(state: CheckoutUiState): Long {
        val subtotal = subtotal()
        val discount = state.promo?.discount ?: 0
        val delivery =
            if (state.method == "courier" && state.promo?.freeShipping != true) {
                state.deliveryCost ?: 0
            } else {
                0
            }
        return (subtotal - discount).coerceAtLeast(0) + delivery
    }

    fun onPromoInput(value: String) =
        _uiState.update { it.copy(promoInput = value, promoError = null) }

    fun onMethodChange(method: String) =
        _uiState.update { it.copy(method = method, addressError = null) }

    fun onAddressChange(value: String) =
        _uiState.update { it.copy(address = value, addressError = null) }

    /** Подстановка адреса из адресной книги (FR-B11). */
    fun onPickAddress(value: String) =
        _uiState.update { it.copy(address = value, addressError = null) }

    fun onSaveAddressChange(value: Boolean) =
        _uiState.update { it.copy(saveAddress = value) }

    /** Серверный предпросмотр промокода (FR-A06 /preview) на текущей корзине. */
    fun applyPromo() {
        val code = _uiState.value.promoInput.trim().uppercase()
        if (code.isEmpty() || _uiState.value.busy) return
        viewModelScope.launch {
            val result = safeApiCall(json) {
                api.previewPromocode(
                    storeId,
                    PromoPreviewRequest(
                        code = code,
                        subtotal = subtotal().toInt(),
                        itemProductIds = items.value.map { it.productId },
                    ),
                )
            }
            when (result) {
                is ApiResult.Success ->
                    if (result.data.valid) {
                        _uiState.update {
                            it.copy(
                                promo = AppliedPromo(
                                    code = code,
                                    discount = result.data.discount.toLong(),
                                    freeShipping = result.data.freeShipping,
                                ),
                                promoError = null,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(promo = null, promoError = result.data.reason ?: "Промокод не применим")
                        }
                    }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(promo = null, promoError = result.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(promo = null, promoError = "Нет соединения с сервером") }
            }
        }
    }

    fun clearPromo() = _uiState.update { it.copy(promo = null, promoInput = "") }

    /** Оформление заказа: сервер пересчитает цены, спишет сток, применит промокод. */
    fun placeOrder() {
        val s = _uiState.value
        val cartItems = items.value
        if (s.busy || cartItems.isEmpty()) return
        // FR-B05: офлайн чекаут заблокирован до восстановления сети (корзина сохраняется).
        if (!online.value) {
            _uiState.update { it.copy(error = "Нет соединения с сервером") }
            return
        }
        if (s.method == "courier" && s.address.isBlank()) {
            _uiState.update { it.copy(addressError = "Укажите адрес доставки") }
            return
        }

        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val request = CheckoutRequest(
                storeId = storeId,
                items = cartItems.map {
                    CheckoutItemDto(
                        productId = it.productId,
                        qty = it.quantity,
                        variant = VariantKeyParser.parse(it.variantKey),
                    )
                },
                promoCode = s.promo?.code,
                delivery = CheckoutDeliveryDto(
                    method = s.method,
                    address = s.address.trim().ifEmpty { null },
                ),
                idempotencyKey = idempotencyKey,
            )
            when (val result = ordersRepository.checkout(request)) {
                is ApiResult.Success -> {
                    cartRepository.clear(storeId)
                    // FR-B11: сохранить адрес доставки в адресную книгу (best-effort)
                    if (s.method == "courier" && s.saveAddress && s.address.isNotBlank()) {
                        addressBook.save(storeId, s.savedAddresses, s.address)
                    }
                    _uiState.update { it.copy(busy = false, placedOrder = result.data) }
                }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = result.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }
}
