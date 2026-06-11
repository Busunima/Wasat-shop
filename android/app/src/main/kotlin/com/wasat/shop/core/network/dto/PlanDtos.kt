package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO тарифа и использования — контракт GET /api/stores/{id}/plan (FR-S03). */

@Serializable
data class PlanLimitsDto(
    /** null — без ограничения (enterprise). */
    val maxProducts: Int? = null,
    val maxStaff: Int? = null,
)

@Serializable
data class PlanUsageCountsDto(
    val products: Int = 0,
    val staff: Int = 0,
)

@Serializable
data class PlanUsageDto(
    val plan: String,
    val limits: PlanLimitsDto = PlanLimitsDto(),
    val usage: PlanUsageCountsDto = PlanUsageCountsDto(),
)
