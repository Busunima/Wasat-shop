package com.wasat.shop.feature.admin

/**
 * Валидация формы промокода — зеркало zod-схемы сервера (server/src/schemas/promocode.ts):
 * code 3..32 `^[A-Z0-9][A-Z0-9-]*$`, percent value 1..100, fixed value ≥0,
 * minAmount ≥0, usageLimit ≥1. Возвращают null при успехе. Pure JVM.
 */
object PromoFormValidation {
    private val CODE_REGEX = Regex("^[A-Z0-9][A-Z0-9-]*$")
    const val CODE_MIN = 3
    const val CODE_MAX = 32

    /** Нормализация ввода кода: верхний регистр, без пробелов. */
    fun normalizeCode(input: String): String = input.trim().uppercase()

    fun validateCode(input: String): String? {
        val code = normalizeCode(input)
        return when {
            code.length < CODE_MIN -> "Код не короче $CODE_MIN символов"
            code.length > CODE_MAX -> "Код не длиннее $CODE_MAX символов"
            !CODE_REGEX.matches(code) -> "Код: латиница A-Z, цифры, дефис"
            else -> null
        }
    }

    /** value: для percent — 1..100; для fixed — минорные единицы ≥0; free_shipping — игнор. */
    fun validateValue(type: String, input: String, currencyCode: String): String? = when (type) {
        "free_shipping" -> null
        "percent" -> when (val n = input.trim().toIntOrNull()) {
            null -> "Процент — целое число"
            else -> if (n in 1..100) null else "Процент: 1..100"
        }
        else -> when {
            input.isBlank() -> "Укажите сумму скидки"
            com.wasat.shop.core.util.PriceParser.parse(input, currencyCode) == null ->
                "Некорректная сумма"
            else -> null
        }
    }

    /** minAmount опционален: пусто → 0; иначе валидная неотрицательная сумма. */
    fun validateMinAmount(input: String, currencyCode: String): String? = when {
        input.isBlank() -> null
        com.wasat.shop.core.util.PriceParser.parse(input, currencyCode) == null ->
            "Некорректная сумма"
        else -> null
    }

    /** usageLimit опционален: пусто → без лимита; иначе целое ≥1. */
    fun validateUsageLimit(input: String): String? = when {
        input.isBlank() -> null
        (input.trim().toIntOrNull() ?: 0) < 1 -> "Лимит — целое число ≥ 1"
        else -> null
    }
}
