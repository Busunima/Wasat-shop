package com.wasat.shop.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.ProductDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Loaded(val products: List<ProductDto>) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    private val _uiState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = CatalogUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.listProducts(storeId)) {
                is ApiResult.Success -> CatalogUiState.Loaded(result.data.items)
                is ApiResult.ApiError -> CatalogUiState.Error(result.message)
                is ApiResult.NetworkError -> CatalogUiState.Error(
                    "Нет соединения с сервером",
                )
            }
        }
    }
}
