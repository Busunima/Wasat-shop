package com.wasat.shop.core.network

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Делает успешные GET-ответы пригодными для хранения в кэше OkHttp (Фаза 0).
 * `max-age=0` — онлайн всегда ревалидируем (поведение «всегда свежее» сохраняется),
 * но ответ кладётся в кэш и доступен офлайн через {@link OfflineCacheInterceptor}.
 * `Vary: Authorization` разделяет кэш по пользователю — чужие данные не утекут.
 * Сетевой интерсептор (а не application), чтобы переписать заголовки до записи в кэш.
 */
@Singleton
class CacheControlInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (chain.request().method != "GET" || !response.isSuccessful) return response
        return response.newBuilder()
            .removeHeader("Pragma")
            .header("Cache-Control", "private, max-age=0")
            .header("Vary", "Authorization")
            .build()
    }
}
