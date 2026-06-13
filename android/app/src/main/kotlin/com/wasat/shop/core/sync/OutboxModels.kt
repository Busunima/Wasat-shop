package com.wasat.shop.core.sync

import kotlinx.serialization.Serializable

/** Типы операций в outbox-очереди (Фаза 2). Сериализуются в PendingOperationEntity.type. */
object OutboxType {
    const val ORDER_STATUS = "order_status"
}

/** Полезная нагрузка операции смены статуса заказа (FR-A04). */
@Serializable
data class OrderStatusOp(
    val orderId: String,
    val status: String,
    val trackingNo: String? = null,
)

/** Результат попытки доставки одной операции. */
enum class DispatchResult {
    /** Доставлено или перманентно отклонено сервером (4xx) — удалить из очереди. */
    DONE,

    /** Сетевая ошибка — оставить в очереди и повторить позже. */
    RETRY,
}
