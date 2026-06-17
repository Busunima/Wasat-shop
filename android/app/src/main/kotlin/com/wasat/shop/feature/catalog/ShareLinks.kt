package com.wasat.shop.feature.catalog

/**
 * Публичные ссылки для шеринга (FR-B12). Используется App Link витрины
 * https://app.example.com/s/{slug} (см. AndroidManifest) — открывает магазин
 * через StoreResolver. Pure JVM — под unit-тестом.
 */
object ShareLinks {
    private const val BASE = "https://app.example.com/s/"

    fun storeUrl(slug: String): String = BASE + slug
}

/** Данные магазина для шеринга (slug → ссылка, name → текст). */
data class StoreShareInfo(val slug: String, val name: String)
