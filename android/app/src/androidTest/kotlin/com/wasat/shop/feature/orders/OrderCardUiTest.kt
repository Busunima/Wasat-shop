package com.wasat.shop.feature.orders

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wasat.shop.R
import com.wasat.shop.core.designsystem.WasatTheme
import com.wasat.shop.core.network.dto.CheckoutVariantDto
import com.wasat.shop.core.network.dto.OrderDeliveryDto
import com.wasat.shop.core.network.dto.OrderDto
import com.wasat.shop.core.network.dto.OrderItemDto
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI-тест карточки заказа (FR-A04/B06): номер, статус, позиции,
 * итог и слот действий рендерятся из переданного состояния.
 */
class OrderCardUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val order = OrderDto(
        id = "abcdef1234567890",
        customerUid = "buyer",
        items = listOf(
            OrderItemDto(
                productId = "p1",
                name = "Кеды",
                qty = 2,
                price = 5000,
                variant = CheckoutVariantDto(size = "M"),
            ),
        ),
        subtotal = 10000,
        total = 10000,
        currency = "USD",
        status = "DELIVERED",
        delivery = OrderDeliveryDto(trackingNo = "TRK-9"),
    )

    @Test
    fun rendersNumberStatusItemAndActions() {
        composeTestRule.setContent {
            WasatTheme {
                OrderCard(order = order, currency = "USD") {
                    Text("ACTION_SLOT")
                }
            }
        }

        val activity = composeTestRule.activity
        // Номер заказа (#первые 8 символов id)
        composeTestRule
            .onNodeWithText(activity.getString(R.string.order_number, "abcdef12"))
            .assertIsDisplayed()
        // Статус (локализованная подпись DELIVERED)
        composeTestRule
            .onNodeWithText(activity.getString(R.string.order_status_delivered))
            .assertIsDisplayed()
        // Позиция заказа (имя + вариант + количество)
        composeTestRule.onNodeWithText("Кеды", substring = true).assertIsDisplayed()
        // Трек-номер
        composeTestRule
            .onNodeWithText(activity.getString(R.string.order_tracking, "TRK-9"))
            .assertIsDisplayed()
        // Слот действий вызывающего отрендерен
        composeTestRule.onNodeWithText("ACTION_SLOT").assertIsDisplayed()
    }
}
