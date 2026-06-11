package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO товара — контракт GET/POST /api/stores/{storeId}/products (docs/data-model.md). */
@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val description: String = "",
    /** Цена в минорных единицах валюты магазина. */
    val price: Long,
    val originalPrice: Long? = null,
    val images: List<String> = emptyList(),
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val variants: List<VariantDto> = emptyList(),
    val totalStock: Int = 0,
    val status: String = "active",
    val sku: String? = null,
    val barcode: String? = null,
    /** Агрегаты отзывов (FR-B08), считает сервер. */
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
)

@Serializable
data class VariantDto(
    val size: String? = null,
    val color: String? = null,
    val stock: Int,
    val sku: String? = null,
)

@Serializable
data class ProductListResponse(
    val items: List<ProductDto>,
    /** null — страниц больше нет (курсорная пагинация FR-B02). */
    val nextCursor: String? = null,
)

/**
 * Тело создания/обновления товара (админ, FR-A02). Форма владеет полным состоянием,
 * поэтому ВСЕ поля кодируются всегда (без дефолтов) — иначе partial-PATCH сервера
 * сохранил бы старые значения при их очистке в форме. Очистка: originalPrice — null,
 * sku/barcode/category — "" (сервер нормализует в null), tags/images — пустой список.
 */
@Serializable
data class ProductUpsertRequest(
    val name: String,
    val price: Long,
    val description: String,
    val status: String,
    val images: List<String>,
    val variants: List<VariantDto>,
    val originalPrice: Long?,
    val sku: String,
    val barcode: String,
    val category: String,
    val tags: List<String>,
)
