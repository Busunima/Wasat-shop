package com.wasat.shop.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Наблюдатель сетевой доступности (offline-first, Фаза 0). Даёт реактивный
 * `online: Flow<Boolean>` для UI-баннера и синхронный `isOnline()` для
 * OkHttp-интерсептора офлайн-кэша. «Онлайн» = есть интернет И сеть валидирована.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean {
        val network = cm?.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    val online: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnline())
            }

            override fun onLost(network: Network) {
                trySend(isOnline())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            }
        }
        trySend(isOnline())
        cm?.registerDefaultNetworkCallback(callback)
        awaitClose { cm?.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
