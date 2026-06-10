package com.wasat.shop.feature.onboarding

/**
 * Клиентская валидация формы магазина — зеркало zod-схемы сервера
 * (server/src/schemas/store.ts): name 1..120, slug 3..40 + регэксп, currency ISO-4217.
 * Возвращают null при успехе, иначе текст ошибки. Pure JVM — покрыто unit-тестами.
 */
object StoreValidation {
    val SLUG_REGEX = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

    const val NAME_MAX = 120
    const val SLUG_MIN = 3
    const val SLUG_MAX = 40
    const val DESCRIPTION_MAX = 2000

    fun validateName(name: String): String? = when {
        name.isBlank() -> "Укажите название магазина"
        name.length > NAME_MAX -> "Название не длиннее $NAME_MAX символов"
        else -> null
    }

    fun validateSlug(slug: String): String? = when {
        slug.length < SLUG_MIN -> "Slug не короче $SLUG_MIN символов"
        slug.length > SLUG_MAX -> "Slug не длиннее $SLUG_MAX символов"
        !SLUG_REGEX.matches(slug) -> "Только строчные латинские буквы, цифры и дефис"
        else -> null
    }

    fun validateCurrency(code: String): String? = when {
        code.length != 3 || !code.all { it in 'A'..'Z' } -> "Код валюты — 3 заглавные буквы (ISO-4217)"
        else -> null
    }

    fun validateDescription(description: String): String? = when {
        description.length > DESCRIPTION_MAX -> "Описание не длиннее $DESCRIPTION_MAX символов"
        else -> null
    }

    /** Предлагает slug из названия: lowercase, не-[a-z0-9] → '-', дефисы схлопнуты/обрезаны. */
    fun suggestSlug(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(SLUG_MAX)
            .trim('-')
}
