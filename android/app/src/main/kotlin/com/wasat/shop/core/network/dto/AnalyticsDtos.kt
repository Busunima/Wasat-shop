package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO событий и аналитики (§16, FR-A05). */

@Serializable
data class AnalyticsEventRequest(
    val type: String,
    val productId: String? = null,
    val value: Long? = null,
    val qty: Int? = null,
    val query: String? = null,
)

@Serializable
data class FunnelDto(
    val views: Int = 0,
    val addToCarts: Int = 0,
    val checkouts: Int = 0,
    val purchases: Int = 0,
)

@Serializable
data class ConversionDto(
    val viewToCart: Double = 0.0,
    val cartToOrder: Double = 0.0,
    val viewToOrder: Double = 0.0,
)

@Serializable
data class TopProductDto(val productId: String, val views: Int)

@Serializable
data class DailyPointDto(
    val date: String,
    val views: Int = 0,
    val orders: Int = 0,
    val revenue: Long = 0,
)

@Serializable
data class AnalyticsReportDto(
    val from: String,
    val to: String,
    val revenue: Long = 0,
    val orders: Int = 0,
    val avgCheck: Long = 0,
    val searches: Int = 0,
    val funnel: FunnelDto = FunnelDto(),
    val conversion: ConversionDto = ConversionDto(),
    val topProducts: List<TopProductDto> = emptyList(),
    val daily: List<DailyPointDto> = emptyList(),
)
