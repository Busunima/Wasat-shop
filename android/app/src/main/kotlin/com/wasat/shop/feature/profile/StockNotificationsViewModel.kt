package com.wasat.shop.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.feature.catalog.CatalogRepository
import com.wasat.shop.feature.wishlist.StockNotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Управление подписками «сообщить о поступлении» в профиле (ТЗ FR-B10): список товаров
 * с активной подпиской + отписка. Источник — `StockNotifyRepository.observe`; названия
 * товаров подтягиваются через `CatalogRepository.getProduct`.
 */
@HiltViewModel
class StockNotificationsViewModel @Inject constructor(
    private val stockNotify: StockNotifyRepository,
    private val catalog: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    /** Подписки доступны только авторизованным (как и в карточке товара). */
    val available: Boolean get() = stockNotify.isAvailable

    /** Товары с активной подпиской; пересобирается при изменении набора (после отписки). */
    val items: StateFlow<List<ProductDto>> = stockNotify.observe(storeId)
        .map { ids ->
            ids.mapNotNull { id ->
                (catalog.getProduct(storeId, id) as? ApiResult.Success)?.data
            }.sortedBy { it.name }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unsubscribe(productId: String) {
        viewModelScope.launch {
            // subscribed=true → arrayRemove (снять подписку).
            stockNotify.toggle(storeId, productId, subscribed = true)
        }
    }
}
