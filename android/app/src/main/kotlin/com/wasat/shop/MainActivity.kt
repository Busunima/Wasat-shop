package com.wasat.shop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.wasat.shop.core.designsystem.WasatTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Единственная Activity (single-activity + Compose-навигация).
 * edge-to-edge обязателен (ТЗ §11.4, на API 36 opt-out игнорируется).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WasatTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    // Каркас: Scaffold корректно обрабатывает insets системных баров (edge-to-edge).
    // Навигация (Compose Navigation) и экраны добавляются в Шаге 2 / Фазе 2.
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Text(text = "Wasat Shop — каркас")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppRootPreview() {
    WasatTheme {
        AppRoot()
    }
}
