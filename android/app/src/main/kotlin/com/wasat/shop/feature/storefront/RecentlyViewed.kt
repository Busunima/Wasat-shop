package com.wasat.shop.feature.storefront

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Снапшот товара для блока «Недавно просмотренные» (FR-B12 MVP, эвристика in-house). */
@Serializable
data class RecentProduct(
    val productId: String,
    val name: String,
    val price: Long,
    val imageUrl: String? = null,
)

/** MRU-логика списка просмотренных: дедупликация, свежие в начале, максимум 10. Pure JVM. */
object RecentMru {
    const val MAX = 10

    fun push(current: List<RecentProduct>, item: RecentProduct): List<RecentProduct> =
        (listOf(item) + current.filter { it.productId != item.productId }).take(MAX)
}

private val Context.recentStore by preferencesDataStore(name = "recently_viewed")

/** Хранилище «недавно просмотренных» помагазинно (DataStore, локально на устройстве). */
@Singleton
class RecentlyViewedRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(storeId: String) = stringPreferencesKey("recent_$storeId")

    fun observe(storeId: String): Flow<List<RecentProduct>> =
        context.recentStore.data.map { prefs ->
            prefs[key(storeId)]?.let { raw ->
                runCatching { json.decodeFromString<List<RecentProduct>>(raw) }.getOrNull()
            } ?: emptyList()
        }

    suspend fun record(storeId: String, item: RecentProduct) {
        context.recentStore.edit { prefs ->
            val current = prefs[key(storeId)]?.let { raw ->
                runCatching { json.decodeFromString<List<RecentProduct>>(raw) }.getOrNull()
            } ?: emptyList()
            prefs[key(storeId)] = json.encodeToString(RecentMru.push(current, item))
        }
    }
}
