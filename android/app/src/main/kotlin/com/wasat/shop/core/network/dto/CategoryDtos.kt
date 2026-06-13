package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO категорий — контракт /api/stores/{id}/categories (FR-A01). */

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val slug: String,
    val parentId: String? = null,
    val order: Int = 0,
    val imageUrl: String? = null,
)

@Serializable
data class CategoryListResponse(val items: List<CategoryDto> = emptyList())

/** Тело создания категории (FR-A01). parentId/imageUrl опускаются, если не заданы. */
@Serializable
data class CategoryCreateRequest(
    val name: String,
    val slug: String,
    val parentId: String? = null,
    val order: Int = 0,
    val imageUrl: String? = null,
)

/**
 * PATCH категории (FR-A01). Форма владеет полным состоянием — все поля без
 * значений по умолчанию, поэтому кодируются всегда (encodeDefaults = false).
 * Очистка: parentId "" → корень, imageUrl "" → без картинки (сервер трактует "").
 */
@Serializable
data class CategoryUpdateRequest(
    val name: String,
    val slug: String,
    val parentId: String,
    val order: Int,
    val imageUrl: String,
)
