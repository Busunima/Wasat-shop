package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** Публичная карточка магазина — GET /api/stores/{storeId} | /by-slug/{slug}. */
@Serializable
data class StoreInfoDto(
    val storeId: String,
    val slug: String,
    val name: String,
    val description: String = "",
    val currency: String,
    val isPublic: Boolean = false,
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val theme: ThemeDto? = null,
    val contact: ContactDto? = null,
    /** Стоимость доставки в минорных единицах; null — не задана. */
    val deliveryCost: Long? = null,
)

@Serializable
data class ThemeDto(val primary: String, val secondary: String)

@Serializable
data class ContactDto(
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
)

/**
 * Тело PATCH /api/stores/{storeId} (FR-A01). Форма владеет полным состоянием —
 * поля кодируются всегда (очистка: "" для url/строк, null для deliveryCost).
 * theme опциональна: null опускается (тема не задана — серверная не трогается).
 */
@Serializable
data class StoreUpdateRequest(
    val name: String,
    val description: String,
    val isPublic: Boolean,
    val logoUrl: String,
    val bannerUrl: String,
    val contact: ContactDto,
    val deliveryCost: Long?,
    val theme: ThemeDto? = null,
)
