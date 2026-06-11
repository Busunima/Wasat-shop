package com.wasat.shop.core.push

import com.google.firebase.messaging.FirebaseMessaging
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.PushTokenRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Регистрация FCM-токена устройства на сервере (FR-B10). Best-effort: без конфига
 * Firebase (messaging == null) или при сбое сети — no-op. Вызывается в момент
 * проявления интереса (добавление в вишлист) — токен попадает в
 * stores/{storeId}/fcmTokens/{uid} и используется для push о наличии/цене.
 */
@Singleton
class PushTokenRepository @Inject constructor(
    private val messaging: FirebaseMessaging?,
    private val api: WasatApi,
) {
    suspend fun register(storeId: String) {
        val fcm = messaging ?: return
        runCatching {
            val token = fcm.token.await()
            api.registerPushToken(storeId, PushTokenRequest(token = token))
        }
    }
}
