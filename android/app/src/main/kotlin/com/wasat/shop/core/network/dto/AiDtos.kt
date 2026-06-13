package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO AI-ассиста — контракт POST /api/stores/{id}/ai/describe (FR-A12). */

@Serializable
data class AiDescribeRequest(
    val name: String,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    /** Свободные подсказки продавца (материал, особенности). */
    val hints: String? = null,
    val language: String = "ru",
    /** "generate" — с нуля; "rewrite" — переписать текущее описание (FR-A12). */
    val mode: String = "generate",
    /** Текущее описание — для режима rewrite. */
    val current: String? = null,
)

@Serializable
data class AiDescribeResponse(val description: String)
