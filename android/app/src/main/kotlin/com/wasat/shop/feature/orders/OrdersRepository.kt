package com.wasat.shop.feature.orders

import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.CheckoutRequest
import com.wasat.shop.core.network.dto.OrderDto
import com.wasat.shop.core.network.dto.OrderListResponse
import com.wasat.shop.core.network.dto.OrderStatusUpdateRequest
import com.wasat.shop.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Заказы (FR-B05/A04/B06): чекаут, списки покупателя/магазина, статусы, отмена. */
@Singleton
class OrdersRepository @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
) {
    suspend fun checkout(body: CheckoutRequest): ApiResult<OrderDto> =
        safeApiCall(json) { api.checkout(body) }

    suspend fun myOrders(storeId: String): ApiResult<OrderListResponse> =
        safeApiCall(json) { api.myOrders(storeId, mapOf("limit" to "50")) }

    suspend fun storeOrders(storeId: String, status: String?): ApiResult<OrderListResponse> =
        safeApiCall(json) {
            api.storeOrders(
                storeId,
                buildMap {
                    put("limit", "50")
                    status?.let { put("status", it) }
                },
            )
        }

    suspend fun updateStatus(
        storeId: String,
        orderId: String,
        status: String,
        trackingNo: String? = null,
    ): ApiResult<OrderDto> =
        safeApiCall(json) {
            api.updateOrderStatus(storeId, orderId, OrderStatusUpdateRequest(status, trackingNo))
        }

    suspend fun cancel(storeId: String, orderId: String): ApiResult<OrderDto> =
        safeApiCall(json) { api.cancelOrder(storeId, orderId) }
}
