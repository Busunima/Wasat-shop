package com.wasat.shop.feature.orders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.wasat.shop.R
import com.wasat.shop.core.designsystem.WasatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI-тест ряда звёзд (FR-B08) и его доступности (ТЗ §11):
 * в режиме показа ряд озвучивается одной подписью, в интерактивном — выбор оценки.
 */
class StarRowUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun str(resId: Int, arg: Int): String =
        composeTestRule.activity.getString(resId, arg)

    @Test
    fun displayMode_exposesSingleRatingContentDescription() {
        composeTestRule.setContent {
            WasatTheme { StarRow(rating = 4) }
        }
        composeTestRule
            .onNodeWithContentDescription(str(R.string.a11y_rating, 4))
            .assertIsDisplayed()
    }

    @Test
    fun interactiveMode_tapStarInvokesCallback() {
        var picked = 0
        composeTestRule.setContent {
            WasatTheme { StarRow(rating = 0, onRate = { picked = it }) }
        }
        composeTestRule
            .onNodeWithContentDescription(str(R.string.a11y_rate_stars, 3))
            .performClick()
        assertEquals(3, picked)
    }
}
