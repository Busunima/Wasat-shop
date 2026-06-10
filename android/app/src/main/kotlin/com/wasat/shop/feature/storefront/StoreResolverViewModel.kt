package com.wasat.shop.feature.storefront

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed interface ResolveUiState {
    data object Loading : ResolveUiState
    data class Resolved(val storeId: String, val currency: String) : ResolveUiState
    data class NotFound(val slug: String) : ResolveUiState
    data class Error(val message: String) : ResolveUiState
}

/** Резолв slug → storeId (FR-B01): вход в витрину по deep link / QR. */
@HiltViewModel
class StoreResolverViewModel @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
    private val lastStoreRepository: LastStoreRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val slug: String = checkNotNull(savedStateHandle["slug"])

    private val _uiState = MutableStateFlow<ResolveUiState>(ResolveUiState.Loading)
    val uiState: StateFlow<ResolveUiState> = _uiState.asStateFlow()

    init {
        resolve()
    }

    fun resolve() {
        _uiState.value = ResolveUiState.Loading
        viewModelScope.launch {
            when (val result = safeApiCall(json) { api.getStoreBySlug(slug) }) {
                is ApiResult.Success -> {
                    // FR-B01: кэшируем последний открытый магазин
                    lastStoreRepository.save(
                        slug = result.data.slug,
                        name = result.data.name,
                        currency = result.data.currency,
                    )
                    _uiState.value = ResolveUiState.Resolved(
                        storeId = result.data.storeId,
                        currency = result.data.currency,
                    )
                }
                is ApiResult.ApiError ->
                    _uiState.value = if (result.httpStatus == 404) {
                        ResolveUiState.NotFound(slug)
                    } else {
                        ResolveUiState.Error(result.message)
                    }
                is ApiResult.NetworkError ->
                    _uiState.value = ResolveUiState.Error("Нет соединения с сервером")
            }
        }
    }
}
