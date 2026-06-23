package com.wasat.shop.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.feature.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Профиль покупателя (ТЗ §11.5): аккаунт (email/выход) + переходы к своим заказам и
 * избранному + GDPR-действия (§13): экспорт данных и удаление аккаунта.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val api: WasatApi,
) : ViewModel() {

    /** Email берём один раз для отображения (на выход — обнуляем флаг входа). */
    val email: String? = authRepository.currentUser()?.email

    private val _signedIn = MutableStateFlow(authRepository.currentUser() != null)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Одноразовый JSON-экспорт для передачи в share sheet (GDPR §13). */
    private val _exportJson = MutableStateFlow<String?>(null)
    val exportJson: StateFlow<String?> = _exportJson.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun signOut() {
        authRepository.signOut()
        _signedIn.value = false
    }

    /** GDPR §13: выгрузка своих данных (заказы + профили) → share sheet. */
    fun exportData() {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            runCatching { api.accountExport() }
                .onSuccess { resp ->
                    if (resp.isSuccessful) {
                        _exportJson.value = resp.body()?.string()
                    } else {
                        _message.value = "Не удалось выгрузить данные"
                    }
                }
                .onFailure { _message.value = "Нет соединения с сервером" }
            _busy.value = false
        }
    }

    fun consumeExport() {
        _exportJson.value = null
    }

    fun dismissMessage() {
        _message.value = null
    }

    /** GDPR §13: удаление аккаунта + анонимизация заказов, затем выход. */
    fun deleteAccount() {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            runCatching { api.accountDelete() }
                .onSuccess { resp ->
                    if (resp.isSuccessful) {
                        authRepository.signOut()
                        _signedIn.value = false
                    } else {
                        _message.value = "Не удалось удалить аккаунт"
                    }
                }
                .onFailure { _message.value = "Нет соединения с сервером" }
            _busy.value = false
        }
    }
}
