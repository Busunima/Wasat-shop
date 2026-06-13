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

    // ── Offline-first (Фаза 1): кэш «моих» заказов в Room = источник правды ──────

    /**
     * Поток заказов покупателя из кэша (Room); UI читает только отсюда.
     * Нечитаемая строка кэша (несовместимая схема после обновления) пропускается,
     * а не валит весь поток — как в RecentlyViewed.
     */
    fun observeMyOrders(storeId: String): Flow<List<OrderDto>> =
        orderDao.observe(storeId, SCOPE_MINE).map { rows ->
            rows.mapNotNull { runCatching { json.decodeFromString<OrderDto>(it.json) }.getOrNull() }
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

    // ── Offline-first (Фаза 1b): кэш заказов магазина в Room ─────────────────────

    /** Поток заказов магазина из кэша (Room); кэш отражает последний фильтр. */
    fun observeStoreOrders(storeId: String): Flow<List<OrderDto>> =
        orderDao.observe(storeId, SCOPE_STORE).map { rows ->
            rows.mapNotNull { runCatching { json.decodeFromString<OrderDto>(it.json) }.getOrNull() }
        }

    /** Сетевое обновление кэша заказов магазина по фильтру; UI обновится через Flow. */
    suspend fun refreshStoreOrders(
        storeId: String,
        status: String?,
        fromMs: Long? = null,
        toMs: Long? = null,
        minTotal: Long? = null,
        maxTotal: Long? = null,
        customer: String? = null,
    ): ApiResult<Unit> =
        when (
            val r = storeOrders(storeId, status, fromMs, toMs, minTotal, maxTotal, customer)
        ) {
            is ApiResult.Success -> {
                orderDao.replaceScope(
                    storeId,
                    SCOPE_STORE,
                    r.data.items.map { it.toEntity(storeId, SCOPE_STORE) },
                )
                ApiResult.Success(Unit)
            }
            is ApiResult.ApiError -> r
            is ApiResult.NetworkError -> r
        }

    /**
     * Оптимистичное локальное изменение статуса заказа магазина (outbox, Фаза 2):
     * сразу обновляет кэш → UI отражает смену офлайн. Авторитетный заказ придёт при
     * доставке из очереди. Если заказа нет в кэше — no-op.
     */
    suspend fun optimisticStoreStatus(storeId: String, orderId: String, status: String) {
        val row = orderDao.find(storeId, SCOPE_STORE, orderId) ?: return
        val dto = runCatching { json.decodeFromString<OrderDto>(row.json) }.getOrNull() ?: return
        orderDao.upsert(dto.copy(status = status).toEntity(storeId, SCOPE_STORE))
    }

    suspend fun updateStatus(
        storeId: String,
        orderId: String,
        status: String,
        trackingNo: String? = null,
    ): ApiResult<OrderDto> {
        val r = safeApiCall(json) {
            api.updateOrderStatus(storeId, orderId, OrderStatusUpdateRequest(status, trackingNo))
        }
        if (r is ApiResult.Success) orderDao.upsert(r.data.toEntity(storeId, SCOPE_STORE))
        return r
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
        const val SCOPE_STORE = "store"
    }
}
