package com.wasat.shop.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Входящее уведомление для центра уведомлений в приложении (ТЗ §11.5). Сохраняется
 * при приёме FCM-сообщения; список и счётчик непрочитанных читаются из Room.
 * `id` — messageId FCM (или UUID), чтобы повторная доставка не задваивала запись.
 */
@Entity(tableName = "notification")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    /** Тип из data-полей пуша (order_status / back_in_stock / …), может отсутствовать. */
    val type: String?,
    val receivedAt: Long,
    val read: Boolean,
)
