package com.wasat.shop.feature.orders

import com.wasat.shop.core.db.CachedReturnEntity
import com.wasat.shop.core.db.ReturnDao
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Возвраты (FR-B09/A11): заявка покупателя, очередь магазина, переходы статусов. */
@Singleton
class ReturnsRepository @Inject constructor(
    private val api: WasatApi,
    private val returnDao: ReturnDao,
    private val json: Json,
) {

    // ── Offline-first (B5.3): кэш возвратов магазина в Room ─────────────────────

    /** Поток возвратов магазина из кэша (Room); UI читает отсюда. */
    fun observeStoreReturns(storeId: String): Flow<List<ReturnDto>> =
        returnDao.observe(storeId, SCOPE_STORE).map { rows ->
            rows.mapNotNull { runCatching { json.decodeFromString<ReturnDto>(it.json) }.getOrNull() }
        }

    /** Сетевое обновление кэша возвратов магазина; список обновится через Flow. */
    suspend fun refreshStoreReturns(storeId: String, status: String?): ApiResult<Unit> =
        when (val r = storeReturns(storeId, status)) {
            is ApiResult.Success -> {
                returnDao.replaceScope(
                    storeId,
                    SCOPE_STORE,
                    r.data.items.map { it.toEntity(storeId, SCOPE_STORE) },
                )
                ApiResult.Success(Unit)
            }
            is ApiResult.ApiError -> r
            is ApiResult.NetworkError -> r
        }

    private fun ReturnDto.toEntity(storeId: String, scope: String): CachedReturnEntity =
        CachedReturnEntity(
            storeId = storeId,
            id = id,
            scope = scope,
            status = status,
            createdAt = createdAt ?: 0L,
            json = json.encodeToString(this),
            cachedAt = System.currentTimeMillis(),
        )

    /** Персист изменённого возврата в кэш магазина (после перехода статуса). */
    suspend fun cacheStoreReturn(storeId: String, dto: ReturnDto) {
        returnDao.upsert(dto.toEntity(storeId, SCOPE_STORE))
    }

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
    ): ApiResult<ReturnDto> = persisting(storeId) {
        api.resolveReturn(storeId, returnId, ReturnResolveRequest(action, comment))
    }

    suspend fun receive(storeId: String, returnId: String): ApiResult<ReturnDto> =
        persisting(storeId) { api.receiveReturn(storeId, returnId) }

    suspend fun refund(storeId: String, returnId: String): ApiResult<ReturnDto> =
        persisting(storeId) { api.refundReturn(storeId, returnId) }

    /** Вызов API возврата + персист результата в кэш магазина (список обновится Flow). */
    private suspend fun persisting(
        storeId: String,
        call: suspend () -> retrofit2.Response<ReturnDto>,
    ): ApiResult<ReturnDto> {
        val r = safeApiCall(json) { call() }
        if (r is ApiResult.Success) cacheStoreReturn(storeId, r.data)
        return r
    }

    private companion object {
        const val SCOPE_STORE = "store"
    }
}

/** Статус возврата (зеркало server RETURN_STATUS) + допустимые действия владельца. */
enum class ReturnStatus { REQUESTED, APPROVED, REJECTED, RECEIVED, REFUNDED }

object ReturnStatuses {
    fun parse(raw: String): ReturnStatus? = ReturnStatus.entries.firstOrNull { it.name == raw }
}
