package com.wasat.shop.feature.admin

import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.AiDescribeRequest
import com.wasat.shop.core.network.dto.AiDescribeResponse
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.ProductListResponse
import com.wasat.shop.core.network.dto.ProductUpsertRequest
import com.wasat.shop.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Управление товарами владельцем (FR-A02). Запись доступна только владельцу —
 * сервер проверяет Custom Claims (requireStoreRole); листинг с токеном владельца
 * возвращает все статусы, включая черновики.
 */
@Singleton
class AdminProductsRepository @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
) {
    suspend fun listProducts(storeId: String): ApiResult<ProductListResponse> =
        // Админ-списку пагинация добавится в Фазе 3 (Dashboard); пока первая страница 50
        safeApiCall(json) { api.listProducts(storeId, mapOf("limit" to "50")) }

    suspend fun getProduct(storeId: String, productId: String): ApiResult<ProductDto> =
        safeApiCall(json) { api.getProduct(storeId, productId) }

    suspend fun create(storeId: String, body: ProductUpsertRequest): ApiResult<ProductDto> =
        safeApiCall(json) { api.createProduct(storeId, body) }

    suspend fun update(
        storeId: String,
        productId: String,
        body: ProductUpsertRequest,
    ): ApiResult<ProductDto> =
        safeApiCall(json) { api.updateProduct(storeId, productId, body) }

    suspend fun delete(storeId: String, productId: String): ApiResult<Unit> =
        safeApiCall(json) { api.deleteProduct(storeId, productId) }

    /** AI-генерация описания товара (FR-A12); 501 — если ключ не настроен на сервере. */
    suspend fun aiDescribe(
        storeId: String,
        body: AiDescribeRequest,
    ): ApiResult<AiDescribeResponse> =
        safeApiCall(json) { api.aiDescribe(storeId, body) }
}
