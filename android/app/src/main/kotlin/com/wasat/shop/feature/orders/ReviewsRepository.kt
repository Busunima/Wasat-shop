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
    suspend fun list(
        storeId: String,
        productId: String,
        cursor: String? = null,
    ): ApiResult<ReviewListResponse> =
        safeApiCall(json) {
            val params = buildMap {
                put("limit", "20")
                if (cursor != null) put("cursor", cursor)
            }
            api.listReviews(storeId, productId, params)
        }

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
