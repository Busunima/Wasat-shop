package com.wasat.shop.core.sync

import com.wasat.shop.core.db.PendingOperationDao
import com.wasat.shop.core.db.PendingOperationEntity
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.ConnectivityObserver
import com.wasat.shop.feature.orders.OrdersRepository
import com.wasat.shop.feature.orders.ReturnsRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Очередь исходящих мутаций (offline-first, Фаза 2). Действие сохраняется локально,
 * UI обновляется оптимистично, доставка на сервер — при наличии сети. Дренаж
 * запускается автоматически при появлении сети (collector сети даёт и стартовый
 * прогон) и после каждого enqueue. Сервер принимает идемпотентные ретраи, поэтому
 * повторная доставка не задваивает эффект.
 *
 * Без WorkManager (фоновая синхронизация при закрытом приложении — отдельный шаг):
 * @Singleton с собственным scope, переживающим экраны.
 */
@Singleton
class OutboxRepository @Inject constructor(
    private val dao: PendingOperationDao,
    private val ordersRepository: OrdersRepository,
    private val returnsRepository: ReturnsRepository,
    private val connectivity: ConnectivityObserver,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainMutex = Mutex() // не допускаем параллельных прогонов очереди

    /** Число ожидающих операций — для индикатора синхронизации в UI. */
    val pendingCount: Flow<Int> = dao.observeCount()

    init {
        // Появление сети (и стартовая эмиссия текущего состояния) запускает дренаж.
        scope.launch {
            connectivity.online.collect { online -> if (online) runCatching { drain() } }
        }
    }

    /** Поставить в очередь смену статуса заказа и попытаться доставить сразу. */
    suspend fun enqueueOrderStatus(
        storeId: String,
        orderId: String,
        status: String,
        trackingNo: String?,
    ) {
        dao.upsert(
            PendingOperationEntity(
                opId = UUID.randomUUID().toString(),
                type = OutboxType.ORDER_STATUS,
                storeId = storeId,
                payload = json.encodeToString(OrderStatusOp(orderId, status, trackingNo)),
                createdAt = System.currentTimeMillis(),
                attempts = 0,
            ),
        )
        runCatching { drain() } // best-effort: офлайн просто оставит в очереди
    }

    /** Поставить в очередь переход возврата (approve|reject|receive|refund). */
    suspend fun enqueueReturnAction(
        storeId: String,
        returnId: String,
        action: String,
        comment: String? = null,
    ) {
        dao.upsert(
            PendingOperationEntity(
                opId = UUID.randomUUID().toString(),
                type = OutboxType.RETURN_ACTION,
                storeId = storeId,
                payload = json.encodeToString(ReturnActionOp(returnId, action, comment)),
                createdAt = System.currentTimeMillis(),
                attempts = 0,
            ),
        )
        runCatching { drain() }
    }

    /** Слить очередь по порядку. Сетевые ошибки оставляют операцию на следующий раз. */
    suspend fun drain() {
        if (!connectivity.isOnline()) return
        drainMutex.withLock {
            for (op in dao.getAll()) {
                when (dispatch(op)) {
                    DispatchResult.DONE -> dao.delete(op.opId)
                    DispatchResult.RETRY -> {
                        dao.setAttempts(op.opId, op.attempts + 1)
                        return // нет сети/сервера — прекратить прогон, сохранив порядок
                    }
                }
            }
        }
    }

    private suspend fun dispatch(op: PendingOperationEntity): DispatchResult =
        when (op.type) {
            OutboxType.ORDER_STATUS -> {
                val d = json.decodeFromString<OrderStatusOp>(op.payload)
                when (ordersRepository.updateStatus(op.storeId, d.orderId, d.status, d.trackingNo)) {
                    is ApiResult.Success -> DispatchResult.DONE
                    // 4xx (например, недопустимый переход) — перманентно, не зациклить
                    is ApiResult.ApiError -> DispatchResult.DONE
                    is ApiResult.NetworkError -> DispatchResult.RETRY
                }
            }
            OutboxType.RETURN_ACTION -> {
                val d = json.decodeFromString<ReturnActionOp>(op.payload)
                val result = when (d.action) {
                    "approve", "reject" -> returnsRepository.resolve(op.storeId, d.returnId, d.action, d.comment)
                    "receive" -> returnsRepository.receive(op.storeId, d.returnId)
                    "refund" -> returnsRepository.refund(op.storeId, d.returnId)
                    else -> return DispatchResult.DONE // неизвестное действие — выкинуть
                }
                when (result) {
                    is ApiResult.Success -> DispatchResult.DONE
                    is ApiResult.ApiError -> DispatchResult.DONE
                    is ApiResult.NetworkError -> DispatchResult.RETRY
                }
            }
            else -> DispatchResult.DONE // неизвестный тип — убрать из очереди
        }
}
