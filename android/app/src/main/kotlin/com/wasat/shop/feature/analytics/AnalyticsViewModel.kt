package com.wasat.shop.feature.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.AnalyticsReportDto
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneOffset
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

/** Период дашборда (FR-A05) — последние N дней, включая сегодня. */
enum class AnalyticsPeriod(val days: Long) {
    WEEK(7),
    MONTH(30),
    QUARTER(90),
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

    private val _period = MutableStateFlow(AnalyticsPeriod.MONTH)
    val period: StateFlow<AnalyticsPeriod> = _period.asStateFlow()

    init {
        load()
    }

    /** Смена периода (FR-A05): перезапрос дашборда за новый диапазон. */
    fun selectPeriod(period: AnalyticsPeriod) {
        if (period == _period.value) return
        _period.value = period
        load()
    }

    fun load() {
        _uiState.value = AnalyticsUiState.Loading
        val today = LocalDate.now(ZoneOffset.UTC)
        val from = today.minusDays(_period.value.days - 1)
        val params = mapOf("from" to from.toString(), "to" to today.toString())
        viewModelScope.launch {
            _uiState.value = when (val result = repository.report(storeId, params)) {
                is ApiResult.Success -> AnalyticsUiState.Loaded(result.data)
                is ApiResult.ApiError -> AnalyticsUiState.Error(result.message)
                is ApiResult.NetworkError -> AnalyticsUiState.Error("Нет соединения с сервером")
            }
        }
    }
}
