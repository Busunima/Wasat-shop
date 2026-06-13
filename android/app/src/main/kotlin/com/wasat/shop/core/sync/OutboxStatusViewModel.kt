package com.wasat.shop.core.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Число ожидающих синхронизации мутаций (outbox, Фаза 2) для глобального
 * индикатора. Инъекция OutboxRepository здесь же поднимает синглтон при старте
 * приложения — его collector сети сливает накопленную офлайн очередь.
 */
@HiltViewModel
class OutboxStatusViewModel @Inject constructor(
    outbox: OutboxRepository,
) : ViewModel() {
    val pending: StateFlow<Int> =
        outbox.pendingCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
