package com.wasat.shop.core.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
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

    @Provides
    @Singleton
    fun provideFirebaseStorage(@ApplicationContext context: Context): FirebaseStorage? =
        if (isFirebaseConfigured(context)) FirebaseStorage.getInstance() else null

    @Provides
    @Singleton
    fun provideFirebaseFirestore(@ApplicationContext context: Context): FirebaseFirestore? =
        if (isFirebaseConfigured(context)) FirebaseFirestore.getInstance() else null

    @Provides
    @Singleton
    fun provideFirebaseMessaging(@ApplicationContext context: Context): FirebaseMessaging? =
        if (isFirebaseConfigured(context)) FirebaseMessaging.getInstance() else null

    /**
     * Remote Config для force-update (§11.5). Дефолт min_supported_version_code=1
     * зашит в приложение — без правок в консоли Firebase обновление не навязывается
     * (fail-open). Без конфига Firebase — null (CI/чистый checkout).
     */
    @Provides
    @Singleton
    fun provideRemoteConfig(@ApplicationContext context: Context): FirebaseRemoteConfig? {
        if (!isFirebaseConfigured(context)) return null
        val rc = FirebaseRemoteConfig.getInstance()
        rc.setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build(),
        )
        rc.setDefaultsAsync(mapOf("min_supported_version_code" to 1L))
        return rc
    }
}
