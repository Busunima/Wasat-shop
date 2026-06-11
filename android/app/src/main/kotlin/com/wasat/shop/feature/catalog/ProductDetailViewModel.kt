package com.wasat.shop.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.VariantDto
import com.wasat.shop.feature.analytics.AnalyticsRepository
import com.wasat.shop.feature.cart.CartRepository
import com.wasat.shop.feature.storefront.RecentProduct
import com.wasat.shop.feature.storefront.RecentlyViewedRepository
import com.wasat.shop.feature.wishlist.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProductDetailUiState {
    data object Loading : ProductDetailUiState
    data class Loaded(val product: ProductDto) : ProductDetailUiState
    data class Error(val message: String) : ProductDetailUiState
}

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val cartRepository: CartRepository,
    private val wishlistRepository: WishlistRepository,
    private val recentlyViewed: RecentlyViewedRepository,
    private val analytics: AnalyticsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])
    private val productId: String = checkNotNull(savedStateHandle["productId"])

    /** ♥ доступен только авторизованным (FR-B07). */
    val wishlistAvailable: Boolean get() = wishlistRepository.isAvailable

    val inWishlist: StateFlow<Boolean> = wishlistRepository.observe(storeId)
        .map { productId in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleWishlist() {
        viewModelScope.launch {
            wishlistRepository.toggle(storeId, productId, inWishlist.value)
        }
    }

    private val _uiState = MutableStateFlow<ProductDetailUiState>(ProductDetailUiState.Loading)
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    /** Похожие товары (FR-B12) — подгружаются best-effort после карточки. */
    private val _related = MutableStateFlow<List<ProductDto>>(emptyList())
    val related: StateFlow<List<ProductDto>> = _related.asStateFlow()

    /** Кратковременный флаг «добавлено» для обратной связи на кнопке. */
    private val _justAdded = MutableStateFlow(false)
    val justAdded: StateFlow<Boolean> = _justAdded.asStateFlow()

    init {
        load()
    }

    fun addToCart(currency: String, variant: VariantDto?) {
        val product = (uiState.value as? ProductDetailUiState.Loaded)?.product ?: return
        viewModelScope.launch {
            cartRepository.add(storeId, currency, product, variant)
            analytics.track(storeId, "add_to_cart", productId = product.id)
            _justAdded.value = true
            delay(2_000)
            _justAdded.value = false
        }
    }

    fun load() {
        _uiState.value = ProductDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.getProduct(storeId, productId)) {
                is ApiResult.Success -> {
                    // FR-B12 MVP: фиксируем просмотр для блока «Недавно просмотренные»
                    recentlyViewed.record(
                        storeId,
                        RecentProduct(
                            productId = result.data.id,
                            name = result.data.name,
                            price = result.data.price,
                            imageUrl = result.data.images.firstOrNull(),
                        ),
                    )
                    analytics.track(storeId, "product_view", productId = result.data.id)
                    loadRelated()
                    ProductDetailUiState.Loaded(result.data)
                }
                is ApiResult.ApiError -> ProductDetailUiState.Error(result.message)
                is ApiResult.NetworkError -> ProductDetailUiState.Error(
                    "Нет соединения с сервером",
                )
            }
        }
    }

    private fun loadRelated() {
        viewModelScope.launch {
            _related.value = repository.related(storeId, productId)
        }
    }
}
