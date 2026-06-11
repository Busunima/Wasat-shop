package com.wasat.shop.feature.catalog

import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.ProductListResponse
import com.wasat.shop.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Чтение каталога витрины через REST (FR-B02; решение №9 в docs/decisions.md).
 * Прямое чтение Firestore с офлайн-кэшем (Room) подключается треком «Офлайн» Фазы 2.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
) {
    suspend fun listProducts(
        storeId: String,
        queryParams: Map<String, String>,
    ): ApiResult<ProductListResponse> =
        safeApiCall(json) { api.listProducts(storeId, queryParams) }

    suspend fun getProduct(storeId: String, productId: String): ApiResult<ProductDto> =
        safeApiCall(json) { api.getProduct(storeId, productId) }

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
