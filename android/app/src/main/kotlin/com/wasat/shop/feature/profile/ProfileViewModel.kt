package com.wasat.shop.feature.profile

import androidx.lifecycle.ViewModel
import com.wasat.shop.feature.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Профиль покупателя (ТЗ §11.5): аккаунт (email/выход) + переходы к своим заказам и
 * избранному. Без Firebase/гость — показывает «Гость» (доступ только к публичным экранам).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    /** Email берём один раз для отображения (на выход — обнуляем флаг входа). */
    val email: String? = authRepository.currentUser()?.email

    private val _signedIn = MutableStateFlow(authRepository.currentUser() != null)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    fun signOut() {
        authRepository.signOut()
        _signedIn.value = false
    }
}
