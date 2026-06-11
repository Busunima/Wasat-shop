package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO промокодов — контракт /api/stores/{id}/promocodes (FR-A06). */

@Serializable
data class PromoScopeDto(
    val productIds: List<String>? = null,
    val categories: List<String>? = null,
)

@Serializable
data class PromoDto(
    val code: String,
    /** fixed | percent | free_shipping */
    val type: String,
    val value: Int = 0,
    val minAmount: Int = 0,
    val startsAt: String? = null,
    val expiresAt: String? = null,
    val usageLimit: Int? = null,
    val usedCount: Int = 0,
    val scope: PromoScopeDto? = null,
    val active: Boolean = true,
)

@Serializable
data class PromoListResponse(val items: List<PromoDto> = emptyList())

/**
 * Тело создания промокода (FR-A06). usageLimit/даты опускаются, если не заданы
 * (сервер трактует absent как «без границы»).
 */
@Serializable
data class PromoCreateRequest(
    val code: String,
    val type: String,
    val value: Int,
    val minAmount: Int,
    val usageLimit: Int? = null,
    val startsAt: String? = null,
    val expiresAt: String? = null,
    val active: Boolean = true,
)

/** Запрос предпросмотра скидки для корзины (публичный, FR-B04). */
@Serializable
data class PromoPreviewRequest(
    val code: String,
    val subtotal: Int,
    val itemProductIds: List<String> = emptyList(),
    val itemCategories: List<String> = emptyList(),
)

@Serializable
data class PromoPreviewResponse(
    val code: String,
    val valid: Boolean,
    val discount: Int = 0,
    val freeShipping: Boolean = false,
    val reason: String? = null,
)
