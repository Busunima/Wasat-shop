package com.wasat.shop.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Локальная БД (офлайн-режим, ТЗ §2 слой 1). v1 — корзина; v2 (offline-first
 * Фаза 1) добавляет кэш заказов `cached_order` для чтения без сети. Миграция
 * аддитивная — таблица корзины не трогается. Очередь синхронизации (outbox) —
 * Фаза 2.
 */
@Database(
    entities = [CartItemEntity::class, CachedOrderEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class WasatDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao
    abstract fun orderDao(): OrderDao

    companion object {
        /** v1 → v2: добавить таблицу кэша заказов (корзина не меняется). */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_order` (" +
                        "`storeId` TEXT NOT NULL, " +
                        "`id` TEXT NOT NULL, " +
                        "`scope` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`json` TEXT NOT NULL, " +
                        "`cachedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`storeId`, `id`))",
                )
            }
        }
    }
}
