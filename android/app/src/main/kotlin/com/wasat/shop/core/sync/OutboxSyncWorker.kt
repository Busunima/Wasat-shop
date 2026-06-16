package com.wasat.shop.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Фоновая синхронизация outbox (offline-first, Фаза 2): периодически и при
 * появлении сети сливает очередь исходящих мутаций, даже когда приложение закрыто.
 * Дополняет дренаж в OutboxRepository (при возврате сети с открытым экраном).
 */
@HiltWorker
class OutboxSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val outbox: OutboxRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        runCatching { outbox.drain() }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )

    companion object {
        const val UNIQUE_NAME = "outbox-sync"
    }
}
