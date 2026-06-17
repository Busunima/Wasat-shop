package com.wasat.shop.feature.catalog

import com.wasat.shop.core.db.CachedProductEntity
import com.wasat.shop.core.db.ProductDao
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.ProductListResponse
import com.wasat.shop.core.network.dto.StoreInfoDto
import com.wasat.shop.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Чтение каталога витрины через REST (FR-B02; решение №9 в docs/decisions.md).
 * Карточка товара кэшируется в Room (Фаза 1c) для офлайн-просмотра.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: WasatApi,
    private val productDao: ProductDao,
    private val json: Json,
) {
    suspend fun listProducts(
        storeId: String,
        queryParams: Map<String, String>,
    ): ApiResult<ProductListResponse> =
        safeApiCall(json) { api.listProducts(storeId, queryParams) }

    /** Публичная карточка магазина (slug/name) — для шеринга (FR-B12). */
    suspend fun getStore(storeId: String): ApiResult<StoreInfoDto> =
        safeApiCall(json) { api.getStore(storeId) }

    /** Карточка товара; при успехе кэшируется для офлайн-фолбэка. */
    suspend fun getProduct(storeId: String, productId: String): ApiResult<ProductDto> {
        val r = safeApiCall(json) { api.getProduct(storeId, productId) }
        if (r is ApiResult.Success) {
            productDao.upsert(
                CachedProductEntity(storeId, productId, json.encodeToString(r.data), System.currentTimeMillis()),
            )
        }
        return r
    }

    /** Последняя сохранённая карточка товара (офлайн-фолбэк) или null. */
    suspend fun cachedProduct(storeId: String, productId: String): ProductDto? =
        productDao.find(storeId, productId)
            ?.let { runCatching { json.decodeFromString<ProductDto>(it.json) }.getOrNull() }

    /** Похожие товары (FR-B12) — best-effort: при сбое пустой список. */
    suspend fun related(storeId: String, productId: String, limit: Int = 8): List<ProductDto> =
        when (val r = safeApiCall(json) {
            api.relatedProducts(storeId, productId, mapOf("limit" to limit.toString()))
        }) {
            is ApiResult.Success -> r.data.items
            else -> emptyList()
        }

    /** Популярное (FR-B12) — best-effort: при сбое пустой список. */
    suspend fun popular(storeId: String, limit: Int = 10): List<ProductDto> =
        when (val r = safeApiCall(json) {
            api.popularProducts(storeId, mapOf("limit" to limit.toString()))
        }) {
            is ApiResult.Success -> r.data.items
            else -> emptyList()
        }
}
