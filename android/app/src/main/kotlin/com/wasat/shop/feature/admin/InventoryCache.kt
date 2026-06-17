package com.wasat.shop.feature.admin

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

/** Оптимистичный пересчёт остатка по дельте (offline-first, B5.3). Pure JVM — под тестом. */
object StockMath {
    fun applyDelta(
        items: List<ProductDto>,
        productId: String,
        sku: String?,
        size: String?,
        color: String?,
        delta: Int,
    ): List<ProductDto> = items.map { p ->
        if (p.id != productId) {
            p
        } else if (p.variants.isEmpty()) {
            p.copy(totalStock = (p.totalStock + delta).coerceAtLeast(0))
        } else {
            val idx = p.variants.indexOfFirst { v ->
                if (sku != null) {
                    v.sku == sku
                } else {
                    (size == null || v.size == size) && (color == null || v.color == color)
                }
            }
            if (idx < 0) {
                p
            } else {
                val variants = p.variants.toMutableList()
                val v = variants[idx]
                variants[idx] = v.copy(stock = (v.stock + delta).coerceAtLeast(0))
                p.copy(variants = variants, totalStock = variants.sumOf { it.stock })
            }
        }
    }
}

private val Context.inventoryStore by preferencesDataStore(name = "inventory_cache")

/** Кэш списка товаров инвентаря помагазинно (DataStore) для офлайн-просмотра остатков. */
@Singleton
class InventoryCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(storeId: String) = stringPreferencesKey("inv_$storeId")

    suspend fun save(storeId: String, items: List<ProductDto>) {
        context.inventoryStore.edit { prefs -> prefs[key(storeId)] = json.encodeToString(items) }
    }

    suspend fun load(storeId: String): List<ProductDto> {
        val raw = context.inventoryStore.data.first()[key(storeId)] ?: return emptyList()
        return runCatching { json.decodeFromString<List<ProductDto>>(raw) }.getOrNull() ?: emptyList()
    }
}
