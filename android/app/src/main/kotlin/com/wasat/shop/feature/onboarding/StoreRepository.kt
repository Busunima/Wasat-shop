package com.wasat.shop.feature.onboarding

import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.ErrorEnvelope
import com.wasat.shop.core.network.dto.StoreInitRequest
import com.wasat.shop.core.network.dto.StoreInitResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Вызовы серверного API магазина с маппингом конверта ошибок в [ApiResult]. */
@Singleton
class StoreRepository @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
) {
    suspend fun initStore(request: StoreInitRequest): ApiResult<StoreInitResponse> = try {
        val response = api.initStore(request)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            ApiResult.Success(body)
        } else {
            val raw = response.errorBody()?.string().orEmpty()
            val envelope = runCatching { json.decodeFromString<ErrorEnvelope>(raw) }.getOrNull()
            ApiResult.ApiError(
                code = envelope?.error?.code ?: "INTERNAL",
                message = envelope?.error?.message ?: "Ошибка сервера (${response.code()})",
                httpStatus = response.code(),
            )
        }
    } catch (e: Exception) {
        ApiResult.NetworkError(e)
    }
}
