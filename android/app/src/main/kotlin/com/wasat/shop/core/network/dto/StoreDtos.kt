package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * DTO контракта POST /api/stores/init (server/src/schemas/store.ts, docs/api-contract.md).
 * Поля и валидация зеркалят zod-схему сервера.
 */
@Serializable
data class StoreInitRequest(
    val name: String,
    val slug: String,
    val currency: String,
    val description: String? = null,
)

@Serializable
data class StoreInitResponse(
    val storeId: String,
    val slug: String,
    val onboarding: OnboardingDto,
)

/**
 * Плоское представление union-типа сервера:
 * { deferred: true, reason } | { deferred: false, stripeAccountId, onboardUrl }.
 */
@Serializable
data class OnboardingDto(
    val deferred: Boolean,
    val reason: String? = null,
    val stripeAccountId: String? = null,
    val onboardUrl: String? = null,
)

/** Запрос оформления подписки (FR-S05): тариф basic|pro|enterprise. */
@Serializable
data class SubscriptionCheckoutRequest(val plan: String)

/** Ответ subscription-checkout (FR-S05): { deferred, reason } | { deferred, url }. */
@Serializable
data class SubscriptionCheckoutDto(
    val deferred: Boolean,
    val reason: String? = null,
    val url: String? = null,
)

/** Единый конверт ошибок API: { error: { code, message, details } }. */
@Serializable
data class ErrorEnvelope(val error: ApiErrorBody)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)
