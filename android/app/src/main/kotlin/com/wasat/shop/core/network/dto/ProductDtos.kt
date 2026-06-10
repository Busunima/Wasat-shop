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
)

@Serializable
data class VariantDto(
    val size: String? = null,
    val color: String? = null,
    val stock: Int,
    val sku: String? = null,
)

@Serializable
data class ProductListResponse(val items: List<ProductDto>)

/**
 * Тело создания/обновления товара (админ, FR-A02). Дефолты не кодируются,
 * поэтому null-поля опускаются — совместимо и с create-, и с partial-схемой PATCH.
 */
@Serializable
data class ProductUpsertRequest(
    val name: String,
    val price: Long,
    val description: String? = null,
    val originalPrice: Long? = null,
    val status: String = "draft",
)
