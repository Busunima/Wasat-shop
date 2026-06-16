package com.wasat.shop.feature.catalog

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.ProductDto

/**
 * Источник страниц каталога (FR-B02, Paging 3): ключ — серверный курсор
 * (значение сортировки + id последнего элемента, opaque base64).
 *
 * Offline-first: первая страница дефолтного каталога (без фильтров/поиска)
 * кэшируется в DataStore; при отсутствии сети отдаётся из кэша одной страницей.
 */
class ProductPagingSource(
    private val repository: CatalogRepository,
    private val storeId: String,
    private val filters: CatalogFilters,
    private val feedCache: CatalogFeedCache,
    private val onItemsLoaded: (List<ProductDto>) -> Unit,
) : PagingSource<String, ProductDto>() {

    /** Дефолтная витрина (без фильтров и поиска) — её кэшируем для офлайна. */
    private val isDefaultFeed: Boolean get() = filters.isDefault && filters.query.isBlank()

    override suspend fun load(params: LoadParams<String>): LoadResult<String, ProductDto> {
        val firstPage = params.key == null
        val query = buildMap {
            putAll(filters.toQueryMap())
            put("limit", params.loadSize.coerceAtMost(50).toString())
            params.key?.let { put("cursor", it) }
        }
        return when (val result = repository.listProducts(storeId, query)) {
            is ApiResult.Success -> {
                onItemsLoaded(result.data.items)
                if (firstPage && isDefaultFeed) {
                    runCatching { feedCache.save(storeId, result.data.items) }
                }
                LoadResult.Page(
                    data = result.data.items,
                    prevKey = null, // только вперёд (infinite scroll)
                    nextKey = result.data.nextCursor,
                )
            }
            is ApiResult.ApiError -> LoadResult.Error(CatalogLoadException(result.message))
            is ApiResult.NetworkError -> offlineFallback(firstPage, result.cause)
        }
    }

    /** Офлайн: первая страница дефолтной витрины — из кэша; иначе обычная ошибка. */
    private suspend fun offlineFallback(
        firstPage: Boolean,
        cause: Throwable,
    ): LoadResult<String, ProductDto> {
        if (firstPage && isDefaultFeed) {
            val cached = runCatching { feedCache.load(storeId) }.getOrNull().orEmpty()
            if (cached.isNotEmpty()) {
                onItemsLoaded(cached)
                return LoadResult.Page(data = cached, prevKey = null, nextKey = null)
            }
        }
        return LoadResult.Error(cause)
    }

    // Курсор серверный и не пересчитывается от позиции — рефреш с первой страницы.
    override fun getRefreshKey(state: PagingState<String, ProductDto>): String? = null
}

class CatalogLoadException(message: String) : Exception(message)
