package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO возвратов — контракт /api/stores/{id}/returns (FR-B09/A11). */

@Serializable
data class ReturnItemDto(
    val productId: String,
    val qty: Int,
)

@Serializable
data class ReturnDto(
    val id: String,
    val orderId: String,
    val customerUid: String,
    val items: List<ReturnItemDto> = emptyList(),
    val reason: String = "",
    /** REQUESTED | APPROVED | REJECTED | RECEIVED | REFUNDED */
    val status: String,
    val refundAmount: Long = 0,
    val refundDeferred: Boolean = false,
    val comment: String? = null,
    val createdAt: Long? = null,
)

@Serializable
data class ReturnListResponse(val items: List<ReturnDto> = emptyList())

@Serializable
data class ReturnCreateRequest(
    val orderId: String,
    val items: List<ReturnItemDto>,
    val reason: String,
)

@Serializable
data class ReturnResolveRequest(
    /** approve | reject */
    val action: String,
    val comment: String? = null,
)
