package com.wasat.shop.core.di

import android.content.Context
import androidx.room.Room
import com.wasat.shop.core.db.CartDao
import com.wasat.shop.core.db.OrderDao
import com.wasat.shop.core.db.WasatDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WasatDatabase =
        Room.databaseBuilder(context, WasatDatabase::class.java, "wasat.db")
            .addMigrations(WasatDatabase.MIGRATION_1_2, WasatDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideCartDao(db: WasatDatabase): CartDao = db.cartDao()

    @Provides
    fun provideOrderDao(db: WasatDatabase): OrderDao = db.orderDao()
}
