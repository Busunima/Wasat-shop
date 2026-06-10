package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** Публичная карточка магазина — GET /api/stores/{storeId}. */
@Serializable
data class StoreInfoDto(
    val storeId: String,
    val slug: String,
    val name: String,
    val description: String = "",
    val currency: String,
    val isPublic: Boolean = false,
)
