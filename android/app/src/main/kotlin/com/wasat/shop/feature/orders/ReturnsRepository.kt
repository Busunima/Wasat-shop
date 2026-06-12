package com.wasat.shop.feature.orders

import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.ReturnCreateRequest
import com.wasat.shop.core.network.dto.ReturnDto
import com.wasat.shop.core.network.dto.ReturnItemDto
import com.wasat.shop.core.network.dto.ReturnListResponse
import com.wasat.shop.core.network.dto.ReturnResolveRequest
import com.wasat.shop.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Возвраты (FR-B09/A11): заявка покупателя, очередь магазина, переходы статусов. */
@Singleton
class ReturnsRepository @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
) {
    suspend fun create(
        storeId: String,
        orderId: String,
        items: List<ReturnItemDto>,
        reason: String,
    ): ApiResult<ReturnDto> =
        safeApiCall(json) { api.createReturn(storeId, ReturnCreateRequest(orderId, items, reason)) }

    suspend fun myReturns(storeId: String): ApiResult<ReturnListResponse> =
        safeApiCall(json) { api.myReturns(storeId) }

    suspend fun storeReturns(storeId: String, status: String?): ApiResult<ReturnListResponse> =
        safeApiCall(json) {
            api.storeReturns(
                storeId,
                buildMap {
                    put("limit", "50")
                    status?.let { put("status", it) }
                },
            )
        }

    suspend fun resolve(
        storeId: String,
        returnId: String,
        action: String,
        comment: String? = null,
    ): ApiResult<ReturnDto> =
        safeApiCall(json) { api.resolveReturn(storeId, returnId, ReturnResolveRequest(action, comment)) }

    suspend fun receive(storeId: String, returnId: String): ApiResult<ReturnDto> =
        safeApiCall(json) { api.receiveReturn(storeId, returnId) }

    suspend fun refund(storeId: String, returnId: String): ApiResult<ReturnDto> =
        safeApiCall(json) { api.refundReturn(storeId, returnId) }
}

/** Статус возврата (зеркало server RETURN_STATUS) + допустимые действия владельца. */
enum class ReturnStatus { REQUESTED, APPROVED, REJECTED, RECEIVED, REFUNDED }

object ReturnStatuses {
    fun parse(raw: String): ReturnStatus? = ReturnStatus.entries.firstOrNull { it.name == raw }
}
