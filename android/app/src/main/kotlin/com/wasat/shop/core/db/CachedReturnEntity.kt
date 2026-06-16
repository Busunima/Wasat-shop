package com.wasat.shop.core.db

import androidx.room.Entity

/**
 * Кэш возвратов для офлайн-чтения (offline-first, B5.3). Аналог CachedOrderEntity:
 * `json` — сериализованный ReturnDto; `status`/`createdAt` вынесены для сортировки.
 * `scope` ("mine"/"store") в первичном ключе — возврат может кэшироваться в обоих
 * списках без коллизии.
 */
@Entity(tableName = "cached_return", primaryKeys = ["storeId", "scope", "id"])
data class CachedReturnEntity(
    val storeId: String,
    val id: String,
    val scope: String,
    val status: String,
    val createdAt: Long,
    val json: String,
    val cachedAt: Long,
)
