package com.wasat.shop.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Доступ к кэшу карточек товаров (offline-first, Фаза 1c). */
@Dao
interface ProductDao {

    @Query("SELECT * FROM cached_product WHERE storeId = :storeId AND id = :id LIMIT 1")
    suspend fun find(storeId: String, id: String): CachedProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: CachedProductEntity)
}
