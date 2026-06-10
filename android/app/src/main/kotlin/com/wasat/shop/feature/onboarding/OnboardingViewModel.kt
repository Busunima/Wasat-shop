package com.wasat.shop.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.StoreInitRequest
import com.wasat.shop.feature.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingField { NAME, SLUG, CURRENCY, DESCRIPTION }

sealed interface Submission {
    data object Idle : Submission
    data object Loading : Submission
    data class Failed(val code: String, val message: String) : Submission
    data class Success(val storeId: String, val slug: String, val deferredReason: String?) : Submission
}

data class OnboardingUiState(
    val name: String = "",
    val slug: String = "",
    val currency: String = "USD",
    val description: String = "",
    /** После ручного редактирования slug автоподстановка из name отключается. */
    val slugEdited: Boolean = false,
    val fieldErrors: Map<OnboardingField, String> = emptyMap(),
    val submission: Submission = Submission.Idle,
)

/** Навигационные события, которые экран не должен терять при рекомпозиции. */
sealed interface OnboardingEvent {
    data object NavigateToSignIn : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val storeRepository: StoreRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()

    fun onNameChange(value: String) = _uiState.update { s ->
        s.copy(
            name = value,
            slug = if (s.slugEdited) s.slug else StoreValidation.suggestSlug(value),
            fieldErrors = s.fieldErrors - OnboardingField.NAME,
        )
    }

    fun onSlugChange(value: String) = _uiState.update { s ->
        s.copy(slug = value, slugEdited = true, fieldErrors = s.fieldErrors - OnboardingField.SLUG)
    }

    fun onCurrencyChange(value: String) = _uiState.update { s ->
        s.copy(currency = value.uppercase().take(3), fieldErrors = s.fieldErrors - OnboardingField.CURRENCY)
    }

    fun onDescriptionChange(value: String) = _uiState.update { s ->
        s.copy(description = value, fieldErrors = s.fieldErrors - OnboardingField.DESCRIPTION)
    }

    fun submit() {
        val s = _uiState.value
        if (s.submission is Submission.Loading) return

        val errors = buildMap {
            StoreValidation.validateName(s.name)?.let { put(OnboardingField.NAME, it) }
            StoreValidation.validateSlug(s.slug)?.let { put(OnboardingField.SLUG, it) }
            StoreValidation.validateCurrency(s.currency)?.let { put(OnboardingField.CURRENCY, it) }
            StoreValidation.validateDescription(s.description)?.let { put(OnboardingField.DESCRIPTION, it) }
        }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }

        _uiState.update { it.copy(submission = Submission.Loading) }
        viewModelScope.launch {
            val request = StoreInitRequest(
                name = s.name.trim(),
                slug = s.slug,
                currency = s.currency,
                description = s.description.trim().ifEmpty { null },
            )
            when (val result = storeRepository.initStore(request)) {
                is ApiResult.Success -> {
                    // Сервер выставил custom claims (storeId, role) — форсируем обновление
                    // ID-токена. Сбой не блокирует успех: claims подтянутся при следующем
                    // плановом обновлении токена.
                    authRepository.refreshClaims()
                    _uiState.update {
                        it.copy(
                            submission = Submission.Success(
                                storeId = result.data.storeId,
                                slug = result.data.slug,
                                deferredReason = result.data.onboarding.reason,
                            ),
                        )
                    }
                }

                is ApiResult.ApiError -> when (result.code) {
                    "CONFLICT" -> _uiState.update {
                        it.copy(
                            submission = Submission.Idle,
                            fieldErrors = it.fieldErrors + (OnboardingField.SLUG to "Этот slug уже занят"),
                        )
                    }

                    "UNAUTHENTICATED" -> {
                        authRepository.signOut()
                        _uiState.update { it.copy(submission = Submission.Idle) }
                        _events.emit(OnboardingEvent.NavigateToSignIn)
                    }

                    else -> _uiState.update {
                        it.copy(submission = Submission.Failed(result.code, result.message))
                    }
                }

                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(
                        submission = Submission.Failed(
                            code = "NETWORK",
                            message = "Нет соединения с сервером. Проверьте сеть и попробуйте ещё раз.",
                        ),
                    )
                }
            }
        }
    }
}
