package com.wasat.shop.feature.orders

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wasat.shop.R
import com.wasat.shop.core.network.dto.OrderDto
import com.wasat.shop.core.util.PriceFormatter
import com.wasat.shop.domain.model.OrderStatus

/** Готовый к печати инвойс (FR-A04): HTML документа + id заказа для имени задания. */
data class InvoiceDoc(val orderId: String, val html: String)

/** Локализованная подпись статуса заказа (канонический enum). */
@StringRes
fun statusLabelRes(status: OrderStatus): Int = when (status) {
    OrderStatus.NEW -> R.string.order_status_new
    OrderStatus.CONFIRMED -> R.string.order_status_confirmed
    OrderStatus.PROCESSING -> R.string.order_status_processing
    OrderStatus.SHIPPED -> R.string.order_status_shipped
    OrderStatus.DELIVERED -> R.string.order_status_delivered
    OrderStatus.COMPLETED -> R.string.order_status_completed
    OrderStatus.CANCELLED -> R.string.order_status_cancelled
    OrderStatus.RETURN_REQUESTED -> R.string.order_status_return_requested
    OrderStatus.RETURNED -> R.string.order_status_returned
    OrderStatus.REFUNDED -> R.string.order_status_refunded
}

/** Локализованная подпись пресета периода (FR-A04). */
@StringRes
fun datePresetLabelRes(preset: DatePreset): Int = when (preset) {
    DatePreset.ALL -> R.string.orders_period_all
    DatePreset.WEEK -> R.string.orders_period_week
    DatePreset.MONTH -> R.string.orders_period_month
    DatePreset.QUARTER -> R.string.orders_period_quarter
}

/** Карточка заказа: номер, статус, состав, сумма; actions — слот вызывающего. */
@Composable
fun OrderCard(
    order: OrderDto,
    currency: String,
    actions: @Composable () -> Unit = {},
) {
    val status = OrderTransitions.parse(order.status)
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.order_number, order.id.take(8)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = status?.let { stringResource(statusLabelRes(it)) } ?: order.status,
                    style = MaterialTheme.typography.labelLarge,
                    color = when (status) {
                        OrderStatus.CANCELLED, OrderStatus.REFUNDED ->
                            MaterialTheme.colorScheme.error
                        OrderStatus.COMPLETED, OrderStatus.DELIVERED ->
                            MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            order.items.forEach { item ->
                Text(
                    text = buildString {
                        append(item.name)
                        val label = listOfNotNull(item.variant?.size, item.variant?.color)
                            .joinToString(" · ")
                        if (label.isNotEmpty()) append(" · $label")
                        append(" × ${item.qty}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            order.delivery.trackingNo?.let {
                Text(
                    text = stringResource(R.string.order_tracking, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.order_total),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = PriceFormatter.format(order.total, currency),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            actions()
        }
    }
}
