package com.wasat.shop.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.db.NotificationDao
import com.wasat.shop.core.db.NotificationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Центр уведомлений (§11.5): список входящих + счётчик непрочитанных из Room. */
@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val dao: NotificationDao,
) : ViewModel() {

    val items: StateFlow<List<NotificationEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unread: StateFlow<Int> =
        dao.observeUnreadCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markAllRead() = viewModelScope.launch { dao.markAllRead() }

    fun clearAll() = viewModelScope.launch { dao.clearAll() }
}
