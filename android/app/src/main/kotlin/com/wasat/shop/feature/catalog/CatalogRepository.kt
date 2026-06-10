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
 * Чтение каталога витрины через REST (FR-B02 базово). Прямое чтение Firestore
 * с офлайн-кэшем (Room) подключается треком «Офлайн-режим» Фазы 2.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
) {
    suspend fun listProducts(storeId: String): ApiResult<ProductListResponse> =
        safeApiCall(json) { api.listProducts(storeId) }

    suspend fun getProduct(storeId: String, productId: String): ApiResult<ProductDto> =
        safeApiCall(json) { api.getProduct(storeId, productId) }
}
