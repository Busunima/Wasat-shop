package com.wasat.shop.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Доступ к кэшу заказов (offline-first, Фаза 1). */
@Dao
interface OrderDao {

    @Query(
        "SELECT * FROM cached_order WHERE storeId = :storeId AND scope = :scope " +
            "ORDER BY createdAt DESC",
    )
    fun observe(storeId: String, scope: String): Flow<List<CachedOrderEntity>>

    @Query(
        "SELECT * FROM cached_order WHERE storeId = :storeId AND scope = :scope " +
            "AND id = :id LIMIT 1",
    )
    suspend fun find(storeId: String, scope: String, id: String): CachedOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(order: CachedOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(orders: List<CachedOrderEntity>)

    @Query("DELETE FROM cached_order WHERE storeId = :storeId AND scope = :scope")
    suspend fun clearScope(storeId: String, scope: String)

    /** Полная замена списка для (storeId, scope) одной транзакцией. */
    @Transaction
    suspend fun replaceScope(storeId: String, scope: String, orders: List<CachedOrderEntity>) {
        clearScope(storeId, scope)
        upsertAll(orders)
    }
}
