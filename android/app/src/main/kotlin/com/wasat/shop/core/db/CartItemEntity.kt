package com.wasat.shop.core.db

import androidx.room.Entity

/**
 * Позиция локальной корзины (FR-B04, офлайн-first). Цена фиксируется на момент
 * добавления (минорные единицы) и пересверяется сервером на чекауте (Фаза 4).
 * Ключ — товар+вариант в рамках магазина: один и тот же товар в разных
 * вариантах — разные позиции.
 */
@Entity(tableName = "cart_items", primaryKeys = ["storeId", "productId", "variantKey"])
data class CartItemEntity(
    val storeId: String,
    val productId: String,
    /** Нормализованный вариант ("size=M;color=red") или "" для товара без вариантов. */
    val variantKey: String,
    val name: String,
    val price: Long,
    val currency: String,
    val imageUrl: String?,
    val quantity: Int,
    val addedAt: Long,
)
