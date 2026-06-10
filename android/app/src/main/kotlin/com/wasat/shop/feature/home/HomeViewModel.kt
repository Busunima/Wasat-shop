package com.wasat.shop.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.safeApiCall
import com.wasat.shop.feature.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = HomeUiState.Loading
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
        }
    }
}
