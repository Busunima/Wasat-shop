package com.wasat.shop.core.designsystem

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Класс ширины окна (ТЗ §11.5, адаптивность): прокидывается из MainActivity через
 * CompositionLocal, экраны адаптируют раскладку (ширина форм, rail vs bottom bar —
 * навигационные контейнеры появятся с мультиэкранной навигацией Фазы 2).
 */
val LocalWindowWidthSizeClass = staticCompositionLocalOf { WindowWidthSizeClass.Compact }

/** Удобный предикат: расширенные раскладки (планшет/десктоп/ландшафт). */
val WindowWidthSizeClass.isExpandedLayout: Boolean
    get() = this != WindowWidthSizeClass.Compact
