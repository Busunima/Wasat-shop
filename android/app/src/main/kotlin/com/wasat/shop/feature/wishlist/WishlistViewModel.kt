package com.wasat.shop.feature.wishlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.feature.catalog.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WishlistUiState(
    val loading: Boolean = true,
    val products: List<ProductDto> = emptyList(),
)

/** Вишлист (FR-B07): ids из customers/{uid}.wishlist → карточки через каталог-API. */
@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val wishlist: WishlistRepository,
    private val catalog: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    private val _uiState = MutableStateFlow(WishlistUiState())
    val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            wishlist.observe(storeId).collect { ids ->
                loadProducts(ids)
            }
        }
    }

    private suspend fun loadProducts(ids: Set<String>) {
        if (ids.isEmpty()) {
            _uiState.value = WishlistUiState(loading = false, products = emptyList())
            return
        }
        val loaded = coroutineScope {
            ids.take(50).map { id -> async { catalog.getProduct(storeId, id) } }.awaitAll()
        }
            .filterIsInstance<ApiResult.Success<ProductDto>>()
            .map { it.data }
        _uiState.value = WishlistUiState(loading = false, products = loaded)
    }

    fun remove(productId: String) {
        viewModelScope.launch {
            wishlist.toggle(storeId, productId, inWishlist = true)
        }
    }
}
