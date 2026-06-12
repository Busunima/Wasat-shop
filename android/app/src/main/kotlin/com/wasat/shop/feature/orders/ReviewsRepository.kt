package com.wasat.shop.feature.orders

import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.ReviewCreateRequest
import com.wasat.shop.core.network.dto.ReviewDto
import com.wasat.shop.core.network.dto.ReviewListResponse
import com.wasat.shop.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Отзывы (FR-B08): публичный список товара + создание/обновление покупателем. */
@Singleton
class ReviewsRepository @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
) {
    suspend fun list(storeId: String, productId: String): ApiResult<ReviewListResponse> =
        safeApiCall(json) { api.listReviews(storeId, productId, mapOf("limit" to "30")) }

    suspend fun create(
        storeId: String,
        productId: String,
        rating: Int,
        text: String?,
        orderId: String,
    ): ApiResult<ReviewDto> =
        safeApiCall(json) {
            api.createReview(
                storeId,
                productId,
                ReviewCreateRequest(rating = rating, text = text, orderId = orderId),
            )
        }
}
