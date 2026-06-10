package com.wasat.shop.core.util

import java.math.BigDecimal
import java.util.Currency

/**
 * Парсинг цены из пользовательского ввода в минорные единицы (обратное к [PriceFormatter]).
 * Учитывает дробность валюты (USD: "129.90" → 12990; JPY: "500" → 500).
 * Pure JVM — тестируется юнитами.
 */
object PriceParser {
    /** null — ввод не является корректной ценой для данной валюты. */
    fun parse(input: String, currencyCode: String): Long? {
        val digits = runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }
            .getOrNull() ?: 2
        val normalized = input.trim().replace(',', '.').replace(" ", "")
        if (normalized.isEmpty()) return null

        val value = runCatching { BigDecimal(normalized) }.getOrNull() ?: return null
        if (value.signum() < 0) return null
        // Больше знаков после запятой, чем допускает валюта, — некорректный ввод
        if (value.stripTrailingZeros().scale() > digits) return null

        return runCatching { value.movePointRight(digits).longValueExact() }.getOrNull()
    }
}
