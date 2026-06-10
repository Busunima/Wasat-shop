package com.wasat.shop.core.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Локальная БД (офлайн-режим, ТЗ §2 слой 1). Версия 1 — корзина;
 * кэш каталога и очередь синхронизации добавляются треком «Офлайн» Фазы 2.
 */
@Database(entities = [CartItemEntity::class], version = 1, exportSchema = false)
abstract class WasatDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao
}
