package com.wasat.shop.feature.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.BroadcastRequest
import com.wasat.shop.core.network.dto.DeliveryStatsDto
import com.wasat.shop.core.network.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class BroadcastUiState(
    val title: String = "",
    val body: String = "",
    /** Сегмент адресатов (FR-A07): all | with_orders | no_orders. */
    val segment: String = "all",
    val titleError: String? = null,
    val bodyError: String? = null,
    val sending: Boolean = false,
    val sent: DeliveryStatsDto? = null,
    val error: String? = null,
)

/** Рассылка владельца всем покупателям магазина (FR-A07): заголовок + текст. */
@HiltViewModel
class BroadcastViewModel @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    private val _uiState = MutableStateFlow(BroadcastUiState())
    val uiState: StateFlow<BroadcastUiState> = _uiState.asStateFlow()

    fun onTitle(v: String) = _uiState.update { it.copy(title = v, titleError = null, sent = null) }
    fun onBody(v: String) = _uiState.update { it.copy(body = v, bodyError = null, sent = null) }
    fun onSegment(v: String) = _uiState.update { it.copy(segment = v, sent = null) }

    /** Подставить шаблон (FR-A07): заполняет заголовок и текст. */
    fun applyTemplate(title: String, body: String) = _uiState.update {
        it.copy(title = title, body = body, titleError = null, bodyError = null, sent = null)
    }

    fun send() {
        val title = _uiState.value.title.trim()
        val body = _uiState.value.body.trim()
        val titleError = if (title.isEmpty()) "Введите заголовок" else null
        val bodyError = if (body.isEmpty()) "Введите текст" else null
        if (titleError != null || bodyError != null) {
            _uiState.update { it.copy(titleError = titleError, bodyError = bodyError) }
            return
        }
        if (_uiState.value.sending) return

        _uiState.update { it.copy(sending = true, error = null, sent = null) }
        viewModelScope.launch {
            val segment = _uiState.value.segment
            when (val r = safeApiCall(json) { api.broadcast(storeId, BroadcastRequest(title, body, segment)) }) {
                is ApiResult.Success ->
                    _uiState.update {
                        it.copy(sending = false, sent = r.data, title = "", body = "")
                    }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(sending = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(sending = false, error = "Нет соединения с сервером") }
            }
        }
    }
}
