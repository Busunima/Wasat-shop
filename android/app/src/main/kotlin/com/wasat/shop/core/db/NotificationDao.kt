package com.wasat.shop.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Доступ к центру уведомлений (ТЗ §11.5). Новые сверху, лимит 100. */
@Dao
interface NotificationDao {

    @Query("SELECT * FROM notification ORDER BY receivedAt DESC LIMIT 100")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notification WHERE read = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Query("UPDATE notification SET read = 1 WHERE read = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM notification")
    suspend fun clearAll()
}
