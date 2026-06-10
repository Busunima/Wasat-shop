package com.wasat.shop.core.network

/** Результат вызова API: успех, ошибка контракта (с кодом из docs/api-contract.md) или сбой сети. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>

    data class ApiError(
        val code: String,
        val message: String,
        val httpStatus: Int,
    ) : ApiResult<Nothing>

    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>
}
