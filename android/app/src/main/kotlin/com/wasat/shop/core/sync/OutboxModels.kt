package com.wasat.shop.core.sync

import kotlinx.serialization.Serializable

/** Типы операций в outbox-очереди (Фаза 2). Сериализуются в PendingOperationEntity.type. */
object OutboxType {
    const val ORDER_STATUS = "order_status"
    const val RETURN_ACTION = "return_action"
    const val STOCK_ADJUST = "stock_adjust"
}

/** Полезная нагрузка операции смены статуса заказа (FR-A04). */
@Serializable
data class OrderStatusOp(
    val orderId: String,
    val status: String,
    val trackingNo: String? = null,
)

/** Переход возврата владельцем (FR-A11). action: approve|reject|receive|refund. */
@Serializable
data class ReturnActionOp(
    val returnId: String,
    val action: String,
    val comment: String? = null,
)

/** Корректировка остатка (FR-A03). idempotencyKey стабилен — сервер не задваивает дельту. */
@Serializable
data class StockAdjustOp(
    val productId: String,
    val sku: String? = null,
    val size: String? = null,
    val color: String? = null,
    val delta: Int,
    val idempotencyKey: String,
)

/** Результат попытки доставки одной операции. */
enum class DispatchResult {
    /** Доставлено или перманентно отклонено сервером (4xx) — удалить из очереди. */
    DONE,

    /** Сетевая ошибка — оставить в очереди и повторить позже. */
    RETRY,
}
