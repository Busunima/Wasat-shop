package com.wasat.shop.core.db

import androidx.room.Entity

/**
 * Кэш заказов для офлайн-чтения (offline-first, Фаза 1). Room = единственный
 * источник правды для UI; сеть обновляет таблицу фоном. `json` — сериализованный
 * OrderDto (полная карточка), а `status`/`createdAt` вынесены колонками для
 * сортировки/выборки. `scope` разделяет списки «свои» (покупатель) и «магазин»
 * и входит в первичный ключ — один заказ может кэшироваться в обоих списках.
 */
@Entity(tableName = "cached_order", primaryKeys = ["storeId", "scope", "id"])
data class CachedOrderEntity(
    val storeId: String,
    val id: String,
    /** "mine" — заказы покупателя (FR-B06); "store" — заказы магазина (FR-A04). */
    val scope: String,
    val status: String,
    /** createdAt (epoch ms); 0 — если сервер не вернул дату (для сортировки). */
    val createdAt: Long,
    val json: String,
    val cachedAt: Long,
)
