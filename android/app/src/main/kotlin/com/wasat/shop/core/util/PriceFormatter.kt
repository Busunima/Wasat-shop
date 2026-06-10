package com.wasat.shop.core.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Форматирование цен: суммы хранятся в минорных единицах валюты магазина
 * (docs/data-model.md), показываются в локали пользователя. Pure JVM — тестируется юнитами.
 */
object PriceFormatter {
    fun format(minorUnits: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
            ?: return "$minorUnits $currencyCode"
        val amount = BigDecimal.valueOf(minorUnits).movePointLeft(currency.defaultFractionDigits)
        val format = NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
        return format.format(amount)
    }
}
