package com.wasat.shop.feature.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.AnalyticsReportDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnalyticsUiState {
    data object Loading : AnalyticsUiState
    data class Loaded(val report: AnalyticsReportDto) : AnalyticsUiState
    data class Error(val message: String) : AnalyticsUiState
}

/** Дашборд аналитики владельца (FR-A05). */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: AnalyticsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    private val _uiState = MutableStateFlow<AnalyticsUiState>(AnalyticsUiState.Loading)
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = AnalyticsUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.report(storeId, emptyMap())) {
                is ApiResult.Success -> AnalyticsUiState.Loaded(result.data)
                is ApiResult.ApiError -> AnalyticsUiState.Error(result.message)
                is ApiResult.NetworkError -> AnalyticsUiState.Error("Нет соединения с сервером")
            }
        }
    }
}
