package com.wasat.shop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.wasat.shop.core.designsystem.LocalWindowWidthSizeClass
import com.wasat.shop.core.designsystem.WasatTheme
import com.wasat.shop.feature.auth.AuthRepository
import com.wasat.shop.navigation.WasatNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Единственная Activity (single-activity + Compose-навигация).
 * edge-to-edge обязателен (ТЗ §11.4, на API 36 opt-out игнорируется).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Адаптивность (ТЗ §11.5): экраны читают класс ширины через CompositionLocal.
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(
                LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass,
            ) {
                WasatTheme {
                    AppRoot(authRepository)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(authRepository: AuthRepository) {
    // Scaffold корректно обрабатывает insets системных баров (edge-to-edge).
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            WasatNavHost(authRepository)
        }
    }
}
