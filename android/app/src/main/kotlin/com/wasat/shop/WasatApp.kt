package com.wasat.shop

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

/**
 * Точка входа приложения. Hilt-граф DI инициализируется здесь (ТЗ §2, слой 1).
 */
@HiltAndroidApp
class WasatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // App Check (Play Integrity) — только при наличии конфига Firebase:
        // без google-services.json (CI, чистый checkout) приложение работает без него.
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
        }
    }
}
