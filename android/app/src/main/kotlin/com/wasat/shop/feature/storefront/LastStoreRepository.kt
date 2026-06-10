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

/** Последний открытый магазин (slug+name+валюта). */
data class LastStore(val slug: String, val name: String, val currency: String)

private val Context.dataStore by preferencesDataStore(name = "storefront")

/** Кэширование последнего открытого магазина (ТЗ FR-B01) в DataStore Preferences. */
@Singleton
class LastStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val slugKey = stringPreferencesKey("last_store_slug")
    private val nameKey = stringPreferencesKey("last_store_name")
    private val currencyKey = stringPreferencesKey("last_store_currency")

    val lastStore: Flow<LastStore?> = context.dataStore.data.map { prefs ->
        val slug = prefs[slugKey] ?: return@map null
        LastStore(
            slug = slug,
            name = prefs[nameKey] ?: slug,
            currency = prefs[currencyKey] ?: "USD",
        )
    }

    suspend fun save(slug: String, name: String, currency: String) {
        context.dataStore.edit { prefs ->
            prefs[slugKey] = slug
            prefs[nameKey] = name
            prefs[currencyKey] = currency
        }
    }
}
