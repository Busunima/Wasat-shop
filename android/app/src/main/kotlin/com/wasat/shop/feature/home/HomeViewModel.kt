package com.wasat.shop.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.PlanUsageDto
import com.wasat.shop.core.network.safeApiCall
import com.wasat.shop.feature.auth.AuthRepository
import com.wasat.shop.feature.storefront.LastStore
import com.wasat.shop.feature.storefront.LastStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed interface HomeUiState {
    data object Loading : HomeUiState
    /** Пользователь без магазина (или claims ещё не подтянулись). */
    data object NoStore : HomeUiState
    data class MyStore(val storeId: String, val name: String, val currency: String) : HomeUiState
}

/** Home: определяет магазин пользователя по custom claims и грузит его карточку. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val api: WasatApi,
    private val json: Json,
    lastStoreRepository: LastStoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Тариф и использование владельца (FR-S03); best-effort — null для не-владельца. */
    private val _planUsage = MutableStateFlow<PlanUsageDto?>(null)
    val planUsage: StateFlow<PlanUsageDto?> = _planUsage.asStateFlow()

    /** Последний открытый чужой магазин (FR-B01) — для быстрого возврата. */
    val lastStore: StateFlow<LastStore?> = lastStoreRepository.lastStore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load()
    }

    fun load() {
        _uiState.value = HomeUiState.Loading
        _planUsage.value = null
        viewModelScope.launch {
            val storeId = authRepository.currentClaims().getOrNull()?.storeId
            if (storeId == null) {
                _uiState.value = HomeUiState.NoStore
                return@launch
            }
            _uiState.value = when (val result = safeApiCall(json) { api.getStore(storeId) }) {
                is ApiResult.Success -> HomeUiState.MyStore(
                    storeId = result.data.storeId,
                    name = result.data.name,
                    currency = result.data.currency,
                )
                else -> HomeUiState.NoStore
            }
            // Тариф/использование — best-effort (сотрудник получит 403 → карточка скрыта).
            val plan = safeApiCall(json) { api.storePlan(storeId) }
            if (plan is ApiResult.Success) _planUsage.value = plan.data
        }
    }
}
