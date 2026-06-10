package com.wasat.shop.feature.storefront

import com.wasat.shop.feature.onboarding.StoreValidation

/**
 * Извлечение slug магазина из содержимого QR-кода (FR-B01). Поддерживаются:
 *  - myapp://store/{slug}
 *  - https://<хост>/s/{slug}
 *  - голый slug
 * null — содержимое не похоже на ссылку магазина. Pure JVM — под unit-тестами.
 */
object QrSlugParser {
    private val DEEP_LINK = Regex("^myapp://store/([^/?#]+)", RegexOption.IGNORE_CASE)
    private val WEB_LINK = Regex("^https?://[^/]+/s/([^/?#]+)", RegexOption.IGNORE_CASE)

    fun parse(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        val candidate = DEEP_LINK.find(text)?.groupValues?.get(1)
            ?: WEB_LINK.find(text)?.groupValues?.get(1)
            ?: text
        val slug = candidate.lowercase()
        return slug.takeIf { StoreValidation.validateSlug(it) == null }
    }
}
