package com.wasat.shop.feature.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WriteReviewUiState(
    val rating: Int = 5,
    val text: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

/** Форма отзыва (FR-B08): рейтинг 1..5 + текст; право проверяет сервер по orderId. */
@HiltViewModel
class WriteReviewViewModel @Inject constructor(
    private val repository: ReviewsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])
    private val productId: String = checkNotNull(savedStateHandle["productId"])
    private val orderId: String = checkNotNull(savedStateHandle["orderId"])

    private val _uiState = MutableStateFlow(WriteReviewUiState())
    val uiState: StateFlow<WriteReviewUiState> = _uiState.asStateFlow()

    fun onRating(value: Int) = _uiState.update { it.copy(rating = value.coerceIn(1, 5)) }
    fun onText(value: String) = _uiState.update { it.copy(text = value) }

    fun submit() {
        val s = _uiState.value
        if (s.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = repository.create(
                storeId = storeId,
                productId = productId,
                rating = s.rating,
                text = s.text.trim().ifEmpty { null },
                orderId = orderId,
            )
            _uiState.update {
                when (result) {
                    is ApiResult.Success -> it.copy(busy = false, done = true)
                    is ApiResult.ApiError -> it.copy(busy = false, error = result.message)
                    is ApiResult.NetworkError ->
                        it.copy(busy = false, error = "Нет соединения с сервером")
                }
            }
        }
    }
}
