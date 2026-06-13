package com.wasat.shop.core.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Состояние сети для глобального офлайн-баннера (Фаза 0). По умолчанию — онлайн. */
@HiltViewModel
class ConnectivityViewModel @Inject constructor(
    observer: ConnectivityObserver,
) : ViewModel() {
    val online: StateFlow<Boolean> =
        observer.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
