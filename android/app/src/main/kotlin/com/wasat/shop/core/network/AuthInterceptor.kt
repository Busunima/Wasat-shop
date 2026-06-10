package com.wasat.shop.core.network

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Прикрепляет к каждому запросу:
 *  - Authorization: Bearer <Firebase ID Token> — обязателен (server/middleware/auth.ts);
 *  - X-Firebase-AppCheck — best-effort: в dev/test сервер не требует App Check,
 *    поэтому сбой получения токена не валит запрос.
 *
 * runBlocking безопасен: интерсептор выполняется на IO-потоках OkHttp, не на main.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val auth: FirebaseAuth?,
    private val appCheck: FirebaseAppCheck?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        runBlocking {
            auth?.currentUser?.getIdToken(false)?.await()?.token
                ?.let { builder.header("Authorization", "Bearer $it") }
            runCatching { appCheck?.getToken(false)?.await()?.token }
                .getOrNull()
                ?.let { builder.header("X-Firebase-AppCheck", it) }
        }
        return chain.proceed(builder.build())
    }
}
