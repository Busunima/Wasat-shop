package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** Регистрация FCM-токена устройства — POST /api/stores/{id}/push-tokens (FR-B10). */
@Serializable
data class PushTokenRequest(
    val token: String,
    val platform: String = "android",
)
