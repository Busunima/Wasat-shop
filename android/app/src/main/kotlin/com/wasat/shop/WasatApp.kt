package com.wasat.shop

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.stripe.android.PaymentConfiguration
import com.wasat.shop.core.sync.OutboxSyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Точка входа приложения. Hilt-граф DI инициализируется здесь (ТЗ §2, слой 1).
 * Configuration.Provider — Hilt-интеграция WorkManager (фоновая синхронизация
 * outbox, offline-first Фаза 2).
 */
@HiltAndroidApp
class WasatApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // App Check (Play Integrity) — только при наличии конфига Firebase:
        // без google-services.json (CI, чистый checkout) приложение работает без него.
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
        }
        // Stripe PaymentSheet (FR-B05): без публикуемого ключа не инициализируем.
        if (BuildConfig.STRIPE_PUBLISHABLE_KEY.isNotBlank()) {
            PaymentConfiguration.init(this, BuildConfig.STRIPE_PUBLISHABLE_KEY)
        }
        scheduleOutboxSync()
    }

    /** Периодический слив outbox при наличии сети (фон, даже если приложение закрыто). */
    private fun scheduleOutboxSync() {
        val request = PeriodicWorkRequestBuilder<OutboxSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            OutboxSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
