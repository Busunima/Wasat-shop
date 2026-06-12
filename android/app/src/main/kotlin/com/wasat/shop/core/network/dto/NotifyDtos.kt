package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO рассылок владельца — контракт POST /api/stores/{id}/notify (FR-A07). */

@Serializable
data class BroadcastRequest(
    val title: String,
    val body: String,
)

/** Статистика доставки broadcast-рассылки. */
@Serializable
data class DeliveryStatsDto(
    val targets: Int = 0,
    val success: Int = 0,
    val failure: Int = 0,
)
