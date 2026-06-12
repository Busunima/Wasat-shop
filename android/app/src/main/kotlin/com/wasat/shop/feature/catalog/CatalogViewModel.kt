package com.wasat.shop.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.feature.analytics.AnalyticsRepository
import com.wasat.shop.feature.cart.CartRepository
import com.wasat.shop.feature.storefront.RecentProduct
import com.wasat.shop.feature.storefront.RecentlyViewedRepository
import com.wasat.shop.feature.wishlist.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Каталог (FR-B02): Paging 3 + поиск (debounce 300мс) + фильтры + сортировка. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    cartRepository: CartRepository,
    private val wishlistRepository: WishlistRepository,
    recentlyViewed: RecentlyViewedRepository,
    private val analytics: AnalyticsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    /** ♥ доступен только авторизованным (FR-B07). */
    val wishlistAvailable: Boolean get() = wishlistRepository.isAvailable

    /** Товары в вишлисте — для заливки сердечек в сетке. */
    val wishlistIds: StateFlow<Set<String>> = wishlistRepository.observe(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** «Недавно просмотренные» (FR-B12 MVP) — горизонтальный ряд над сеткой. */
    val recent: StateFlow<List<RecentProduct>> = recentlyViewed.observe(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** «Популярное» (FR-B12) — топ по просмотрам; best-effort, ряд над сеткой. */
    private val _popular = MutableStateFlow<List<ProductDto>>(emptyList())
    val popular: StateFlow<List<ProductDto>> = _popular.asStateFlow()

    init {
        viewModelScope.launch { _popular.value = repository.popular(storeId) }
        // §16: фиксируем поисковый запрос (после debounce) — качество поиска + воронка
        viewModelScope.launch {
            _searchInput.debounce(300).distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collect { analytics.track(storeId, "search", query = it) }
        }
    }

    fun toggleWishlist(productId: String) {
        viewModelScope.launch {
            wishlistRepository.toggle(storeId, productId, productId in wishlistIds.value)
        }
    }

    private val _searchInput = MutableStateFlow("")
    val searchInput: StateFlow<String> = _searchInput.asStateFlow()

    private val _filters = MutableStateFlow(CatalogFilters())
    val filters: StateFlow<CatalogFilters> = _filters.asStateFlow()

    /** Категории, встреченные в загруженных страницах, — источник chip-фильтров. */
    private val _categories = MutableStateFlow<Set<String>>(emptySet())
    val categories: StateFlow<Set<String>> = _categories.asStateFlow()

    /** Число позиций в корзине этого магазина — для бейджа на кнопке корзины. */
    val cartCount: StateFlow<Int> = cartRepository.observeCount(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val products: Flow<PagingData<ProductDto>> =
        combine(
            _filters,
            _searchInput.debounce(300).distinctUntilChanged(), // ТЗ FR-B02: debounce 300мс
        ) { filters, query -> filters.copy(query = query) }
            .distinctUntilChanged()
            .flatMapLatest { effective ->
                Pager(PagingConfig(pageSize = 20, initialLoadSize = 20)) {
                    ProductPagingSource(repository, storeId, effective) { items ->
                        _categories.update { it + items.mapNotNull(ProductDto::category) }
                    }
                }.flow
            }
            .cachedIn(viewModelScope)

    fun onSearchChange(value: String) {
        _searchInput.value = value
    }

    fun onCategoryToggle(category: String) = _filters.update {
        it.copy(category = if (it.category == category) null else category)
    }

    fun onSortChange(sort: CatalogSort) = _filters.update { it.copy(sort = sort) }

    fun onInStockToggle() = _filters.update { it.copy(inStockOnly = !it.inStockOnly) }

    fun onPriceRangeChange(min: Long?, max: Long?) = _filters.update {
        it.copy(minPrice = min, maxPrice = max)
    }

    fun clearFilters() {
        _filters.value = CatalogFilters()
    }
}
