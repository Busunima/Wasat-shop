package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO инвентаря — контракт /api/stores/{id}/inventory/... (FR-A03). */

@Serializable
data class VariantSelectorDto(
    val sku: String? = null,
    val size: String? = null,
    val color: String? = null,
)

@Serializable
data class StockAdjustRequest(
    /** null — товар без вариантов (корректируется totalStock). */
    val variant: VariantSelectorDto? = null,
    val delta: Int,
    val reason: String = "manual",
)

@Serializable
data class StockResultDto(
    val productId: String,
    val totalStock: Int,
    val variants: List<VariantDto> = emptyList(),
)

@Serializable
data class InventoryLogEntryDto(
    val productId: String,
    val variant: String? = null,
    val delta: Int,
    val reason: String,
    val byUid: String,
    val at: Long? = null,
)

@Serializable
data class InventoryLogResponse(val items: List<InventoryLogEntryDto>)

@Serializable
data class ImportRowErrorDto(val line: Int, val raw: String, val message: String)

@Serializable
data class ImportReportDto(
    val applied: Int,
    val errors: List<ImportRowErrorDto> = emptyList(),
)
