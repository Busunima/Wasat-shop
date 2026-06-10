package com.wasat.shop.core.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase через nullable-DI: google-services.json не коммитится (ТЗ §13), поэтому
 * приложение обязано запускаться и без конфига Firebase (CI, чистый checkout) —
 * UI показывает состояние «не сконфигурировано» вместо краша.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    private fun isFirebaseConfigured(context: Context): Boolean =
        FirebaseApp.getApps(context).isNotEmpty()

    @Provides
    @Singleton
    fun provideFirebaseAuth(@ApplicationContext context: Context): FirebaseAuth? =
        if (isFirebaseConfigured(context)) FirebaseAuth.getInstance() else null

    @Provides
    @Singleton
    fun provideFirebaseAppCheck(@ApplicationContext context: Context): FirebaseAppCheck? =
        if (isFirebaseConfigured(context)) FirebaseAppCheck.getInstance() else null
}
