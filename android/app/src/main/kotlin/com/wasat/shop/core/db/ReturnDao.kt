package com.wasat.shop.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Доступ к кэшу возвратов (offline-first, B5.3). Аналог OrderDao. */
@Dao
interface ReturnDao {

    @Query(
        "SELECT * FROM cached_return WHERE storeId = :storeId AND scope = :scope " +
            "ORDER BY createdAt DESC",
    )
    fun observe(storeId: String, scope: String): Flow<List<CachedReturnEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CachedReturnEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedReturnEntity>)

    @Query("DELETE FROM cached_return WHERE storeId = :storeId AND scope = :scope")
    suspend fun clearScope(storeId: String, scope: String)

    /** Полная замена списка для (storeId, scope) одной транзакцией. */
    @Transaction
    suspend fun replaceScope(storeId: String, scope: String, items: List<CachedReturnEntity>) {
        clearScope(storeId, scope)
        upsertAll(items)
    }
}
