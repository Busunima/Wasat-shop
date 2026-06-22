package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO заказов — контракт POST /api/checkout и /api/stores/{id}/orders (FR-B05/A04/B06). */

@Serializable
data class CheckoutVariantDto(
    val sku: String? = null,
    val size: String? = null,
    val color: String? = null,
)

@Serializable
data class CheckoutItemDto(
    val productId: String,
    val qty: Int,
    val variant: CheckoutVariantDto? = null,
)

@Serializable
data class CheckoutDeliveryDto(
    /** pickup | courier */
    val method: String,
    val address: String? = null,
)

@Serializable
data class CheckoutRequest(
    val storeId: String,
    val items: List<CheckoutItemDto>,
    val promoCode: String? = null,
    val delivery: CheckoutDeliveryDto,
    /** Повтор с тем же ключом вернёт тот же заказ (идемпотентность §10.1). */
    val idempotencyKey: String,
    val customerEmail: String? = null,
)

@Serializable
data class OrderItemDto(
    val productId: String,
    val name: String,
    val qty: Int,
    /** Цена за единицу на момент заказа (минорные) — серверный снапшот. */
    val price: Long,
    val variant: CheckoutVariantDto? = null,
)

@Serializable
data class OrderDeliveryDto(
    val method: String = "pickup",
    val address: String? = null,
    val cost: Long = 0,
    val trackingNo: String? = null,
)

@Serializable
data class OrderPaymentDto(
    val method: String = "deferred",
    val paidAt: Long? = null,
)

@Serializable
data class OrderDto(
    val id: String,
    val customerUid: String,
    val customerEmail: String = "",
    val items: List<OrderItemDto> = emptyList(),
    val subtotal: Long = 0,
    val tax: Long = 0,
    val discount: Long = 0,
    val total: Long = 0,
    val currency: String,
    val promoCode: String? = null,
    /** Канонический enum — domain/model/OrderStatus.kt. */
    val status: String,
    val delivery: OrderDeliveryDto = OrderDeliveryDto(),
    val payment: OrderPaymentDto = OrderPaymentDto(),
    /** Причина отмены (FR-A04), если заказ отменён владельцем. */
    val cancelReason: String? = null,
    val createdAt: Long? = null,
)

@Serializable
data class OrderListResponse(val items: List<OrderDto> = emptyList())

@Serializable
data class OrderStatusUpdateRequest(
    val status: String,
    val trackingNo: String? = null,
    /** Причина отмены (FR-A04) — учитывается сервером только для CANCELLED. */
    val reason: String? = null,
)
