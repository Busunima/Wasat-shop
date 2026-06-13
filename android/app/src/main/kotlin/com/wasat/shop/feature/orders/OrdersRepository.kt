package com.wasat.shop.feature.orders

import com.wasat.shop.core.db.CachedOrderEntity
import com.wasat.shop.core.db.OrderDao
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.CheckoutRequest
import com.wasat.shop.core.network.dto.OrderDto
import com.wasat.shop.core.network.dto.OrderListResponse
import com.wasat.shop.core.network.dto.OrderStatusUpdateRequest
import com.wasat.shop.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Заказы (FR-B05/A04/B06): чекаут, списки покупателя/магазина, статусы, отмена. */
@Singleton
class OrdersRepository @Inject constructor(
    private val api: WasatApi,
    private val orderDao: OrderDao,
    private val json: Json,
) {
    suspend fun checkout(body: CheckoutRequest): ApiResult<OrderDto> =
        safeApiCall(json) { api.checkout(body) }

    suspend fun myOrders(storeId: String): ApiResult<OrderListResponse> =
        safeApiCall(json) { api.myOrders(storeId, mapOf("limit" to "50")) }

    // ── Offline-first (Фаза 1): кэш «моих» заказов в Room = источник правды ──────

    /** Поток заказов покупателя из кэша (Room); UI читает только отсюда. */
    fun observeMyOrders(storeId: String): Flow<List<OrderDto>> =
        orderDao.observe(storeId, SCOPE_MINE).map { rows ->
            rows.map { json.decodeFromString<OrderDto>(it.json) }
        }

    /** Сетевое обновление кэша «моих» заказов; список UI обновится через Flow. */
    suspend fun refreshMyOrders(storeId: String): ApiResult<Unit> =
        when (val r = safeApiCall(json) { api.myOrders(storeId, mapOf("limit" to "50")) }) {
            is ApiResult.Success -> {
                orderDao.replaceScope(
                    storeId,
                    SCOPE_MINE,
                    r.data.items.map { it.toEntity(storeId, SCOPE_MINE) },
                )
                ApiResult.Success(Unit)
            }
            is ApiResult.ApiError -> r
            is ApiResult.NetworkError -> r
        }

    private fun OrderDto.toEntity(storeId: String, scope: String): CachedOrderEntity =
        CachedOrderEntity(
            storeId = storeId,
            id = id,
            scope = scope,
            status = status,
            createdAt = createdAt ?: 0L,
            json = json.encodeToString(this),
            cachedAt = System.currentTimeMillis(),
        )

    suspend fun order(storeId: String, orderId: String): ApiResult<OrderDto> =
        safeApiCall(json) { api.order(storeId, orderId) }

    suspend fun storeOrders(
        storeId: String,
        status: String?,
        fromMs: Long? = null,
        toMs: Long? = null,
        minTotal: Long? = null,
        maxTotal: Long? = null,
        customer: String? = null,
    ): ApiResult<OrderListResponse> =
        safeApiCall(json) {
            api.storeOrders(
                storeId,
                buildMap {
                    put("limit", "50")
                    status?.let { put("status", it) }
                    fromMs?.let { put("from", it.toString()) }
                    toMs?.let { put("to", it.toString()) }
                    minTotal?.let { put("minTotal", it.toString()) }
                    maxTotal?.let { put("maxTotal", it.toString()) }
                    customer?.takeIf { it.isNotBlank() }?.let { put("customer", it.trim()) }
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

    suspend fun cancel(storeId: String, orderId: String): ApiResult<OrderDto> {
        val r = safeApiCall(json) { api.cancelOrder(storeId, orderId) }
        if (r is ApiResult.Success) orderDao.upsert(r.data.toEntity(storeId, SCOPE_MINE))
        return r
    }

    /** HTML-инвойс заказа (FR-A04) для печати в PDF на клиенте. */
    suspend fun invoiceHtml(storeId: String, orderId: String): ApiResult<String> =
        when (val r = safeApiCall(json) { api.orderInvoice(storeId, orderId) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.string())
            is ApiResult.ApiError -> r
            is ApiResult.NetworkError -> r
        }

    /** CSV-экспорт заказов магазина (FR-A05); экран отдаёт файл в share sheet. */
    suspend fun exportCsv(storeId: String, status: String?): ApiResult<String> =
        when (
            val r = safeApiCall(json) {
                api.exportOrdersCsv(
                    storeId,
                    buildMap { status?.let { put("status", it) } },
                )
            }
        ) {
            is ApiResult.Success -> ApiResult.Success(r.data.string())
            is ApiResult.ApiError -> r
            is ApiResult.NetworkError -> r
        }

    private companion object {
        const val SCOPE_MINE = "mine"
    }
}
