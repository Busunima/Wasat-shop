package com.wasat.shop.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Очередь исходящих мутаций (offline-first, Фаза 2 — outbox). Действие сначала
 * сохраняется сюда (persist-then-network), затем доставляется на сервер при наличии
 * сети. `opId` — стабильный UUID (переживает рестарт). `payload` — JSON, зависящий
 * от `type`. Сервер принимает безопасные ретраи (идемпотентность статусов/возвратов/
 * остатка), поэтому повторная доставка не задваивает эффект.
 */
@Entity(tableName = "pending_operation")
data class PendingOperationEntity(
    @PrimaryKey val opId: String,
    val type: String,
    val storeId: String,
    val payload: String,
    val createdAt: Long,
    val attempts: Int,
)
