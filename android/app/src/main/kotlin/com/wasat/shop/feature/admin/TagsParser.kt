package com.wasat.shop.feature.admin

/**
 * Теги вводятся одной строкой через запятую; зеркало схемы сервера:
 * ≤20 тегов, каждый 1..40 символов. Pure JVM — под unit-тестами.
 */
object TagsParser {
    const val MAX_TAGS = 20
    const val MAX_TAG_LENGTH = 40

    fun parse(input: String): List<String> =
        input.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** null — ок; иначе текст ошибки. */
    fun validate(input: String): String? {
        val tags = parse(input)
        return when {
            tags.size > MAX_TAGS -> "Не больше $MAX_TAGS тегов"
            tags.any { it.length > MAX_TAG_LENGTH } -> "Тег не длиннее $MAX_TAG_LENGTH символов"
            else -> null
        }
    }

    fun format(tags: List<String>): String = tags.joinToString(", ")
}
