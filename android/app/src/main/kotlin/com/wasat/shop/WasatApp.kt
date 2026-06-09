package com.wasat.shop

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Точка входа приложения. Hilt-граф DI инициализируется здесь (ТЗ §2, слой 1).
 * В Фазе 1 сюда добавляется инициализация Firebase App Check.
 */
@HiltAndroidApp
class WasatApp : Application()
