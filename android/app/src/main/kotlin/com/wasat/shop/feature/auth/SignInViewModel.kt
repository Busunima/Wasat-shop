package com.wasat.shop.feature.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SignInUiState {
    /** Firebase не сконфигурирован (нет google-services.json / Web Client ID). */
    data object NotConfigured : SignInUiState
    data object Idle : SignInUiState
    data object Loading : SignInUiState
    data class Error(val message: String) : SignInUiState
    data object SignedIn : SignInUiState
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private fun initialState(): SignInUiState = when {
        !authRepository.isConfigured -> SignInUiState.NotConfigured
        authRepository.currentUser() != null -> SignInUiState.SignedIn
        else -> SignInUiState.Idle
    }

    /** [activityContext] — Activity-контекст из composable (LocalContext.current). */
    fun signIn(activityContext: Context) {
        if (_uiState.value !is SignInUiState.Idle && _uiState.value !is SignInUiState.Error) return
        _uiState.value = SignInUiState.Loading
        viewModelScope.launch {
            authRepository.signInWithGoogle(activityContext)
                .onSuccess { _uiState.value = SignInUiState.SignedIn }
                .onFailure { e ->
                    _uiState.value = if (e is GetCredentialCancellationException) {
                        SignInUiState.Idle // пользователь сам закрыл диалог — не ошибка
                    } else {
                        SignInUiState.Error(e.message ?: "Не удалось войти")
                    }
                }
        }
    }
}
