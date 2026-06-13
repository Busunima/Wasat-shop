package com.wasat.shop.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Локальная БД (офлайн-режим, ТЗ §2 слой 1). v1 — корзина; v2 (offline-first
 * Фаза 1) добавляет кэш заказов `cached_order`; v3 (Фаза 1b) вносит `scope` в
 * первичный ключ, чтобы заказ кэшировался и как «свой», и как «магазина» без
 * коллизии. Таблица корзины не трогается. Очередь синхронизации (outbox) — Фаза 2.
 */
@Database(
    entities = [CartItemEntity::class, CachedOrderEntity::class, PendingOperationEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class WasatDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao
    abstract fun orderDao(): OrderDao
    abstract fun pendingOperationDao(): PendingOperationDao

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

        /** v2 → v3: `scope` в первичном ключе. cached_order — кэш, безопасно пересоздать. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `cached_order`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_order` (" +
                        "`storeId` TEXT NOT NULL, " +
                        "`id` TEXT NOT NULL, " +
                        "`scope` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`json` TEXT NOT NULL, " +
                        "`cachedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`storeId`, `scope`, `id`))",
                )
            }
        }

        /** v3 → v4: очередь исходящих мутаций (outbox). Аддитивно — кэш не трогаем. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_operation` (" +
                        "`opId` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`storeId` TEXT NOT NULL, " +
                        "`payload` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`attempts` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`opId`))",
                )
            }
        }
    }
}
