package com.wasat.shop.core.db

import androidx.room.Entity

/**
 * Кэш карточек товаров для офлайн-просмотра (offline-first, Фаза 1c). Заполняется
 * при успешном сетевом getProduct; используется как фолбэк, когда сети нет.
 * `json` — сериализованный ProductDto.
 */
@Entity(tableName = "cached_product", primaryKeys = ["storeId", "id"])
data class CachedProductEntity(
    val storeId: String,
    val id: String,
    val json: String,
    val cachedAt: Long,
)
