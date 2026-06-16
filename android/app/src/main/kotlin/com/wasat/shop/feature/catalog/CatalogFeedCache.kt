package com.wasat.shop.feature.catalog

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wasat.shop.core.network.dto.ProductDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.catalogFeedStore by preferencesDataStore(name = "catalog_feed_cache")

/**
 * Кэш первой страницы каталога по умолчанию (offline-first, FR-B02): сохраняется
 * при успешной загрузке без фильтров/поиска, отдаётся как фолбэк, когда сети нет.
 * Локально на устройстве (DataStore) — как RecentlyViewed.
 */
@Singleton
class CatalogFeedCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(storeId: String) = stringPreferencesKey("feed_$storeId")

    suspend fun save(storeId: String, items: List<ProductDto>) {
        context.catalogFeedStore.edit { prefs -> prefs[key(storeId)] = json.encodeToString(items) }
    }

    /** Последняя сохранённая первая страница дефолтного каталога или пустой список. */
    suspend fun load(storeId: String): List<ProductDto> {
        val raw = context.catalogFeedStore.data.first()[key(storeId)] ?: return emptyList()
        return runCatching { json.decodeFromString<List<ProductDto>>(raw) }.getOrNull() ?: emptyList()
    }
}
