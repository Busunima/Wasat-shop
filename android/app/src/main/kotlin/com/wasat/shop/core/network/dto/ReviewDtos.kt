package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO отзывов — контракт /api/stores/{id}/products/{pid}/reviews (FR-B08). */

@Serializable
data class ReviewDto(
    val id: String,
    val productId: String,
    val customerUid: String,
    val rating: Int,
    val text: String? = null,
    val photos: List<String> = emptyList(),
    val orderId: String = "",
    val createdAt: Long? = null,
)

@Serializable
data class ReviewListResponse(
    val items: List<ReviewDto> = emptyList(),
    /** Курсор следующей страницы (FR-B03); null — отзывов больше нет. */
    val nextCursor: String? = null,
)

@Serializable
data class ReviewCreateRequest(
    val rating: Int,
    val text: String? = null,
    val photos: List<String> = emptyList(),
    /** Заказ, подтверждающий покупку (DELIVERED/COMPLETED, содержит товар). */
    val orderId: String,
)
