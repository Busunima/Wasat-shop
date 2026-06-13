package com.wasat.shop.core.network

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Офлайн-фолбэк чтения (Фаза 0): когда сети нет, GET-запросы обслуживаются из
 * дискового кэша OkHttp (onlyIfCached + большой maxStale). Если в кэше пусто —
 * OkHttp вернёт 504, который `safeApiCall` отдаст как NetworkError (как и сейчас).
 * Мутации (не-GET) не трогаем — их офлайн-доставка появится в Фазе 2 (outbox).
 */
@Singleton
class OfflineCacheInterceptor @Inject constructor(
    private val connectivity: ConnectivityObserver,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.method == "GET" && !connectivity.isOnline()) {
            request = request.newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(OFFLINE_MAX_STALE_DAYS, TimeUnit.DAYS)
                        .build(),
                )
                .build()
        }
        return chain.proceed(request)
    }

    private companion object {
        const val OFFLINE_MAX_STALE_DAYS = 7
    }
}
