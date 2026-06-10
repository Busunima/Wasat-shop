package com.wasat.shop.core.network

import com.wasat.shop.core.network.dto.ErrorEnvelope
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Единый маппинг Retrofit-ответа в [ApiResult]: не-2xx декодируется как конверт
 * ошибок сервера ({error:{code,message}}), сетевые/парсинг-сбои — NetworkError.
 */
suspend fun <T> safeApiCall(json: Json, block: suspend () -> Response<T>): ApiResult<T> = try {
    val response = block()
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
