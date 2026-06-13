package com.wasat.shop.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Доступ к очереди исходящих мутаций (outbox, Фаза 2). FIFO по createdAt. */
@Dao
interface PendingOperationDao {

    @Query("SELECT * FROM pending_operation ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingOperationEntity>

    /** Число ожидающих синхронизации операций (для индикатора в UI). */
    @Query("SELECT COUNT(*) FROM pending_operation")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(op: PendingOperationEntity)

    @Query("DELETE FROM pending_operation WHERE opId = :opId")
    suspend fun delete(opId: String)

    @Query("UPDATE pending_operation SET attempts = :attempts WHERE opId = :opId")
    suspend fun setAttempts(opId: String, attempts: Int)
}
