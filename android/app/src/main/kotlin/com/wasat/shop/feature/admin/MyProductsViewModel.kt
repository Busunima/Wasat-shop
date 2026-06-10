package com.wasat.shop.feature.admin

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

sealed interface MyProductsUiState {
    data object Loading : MyProductsUiState
    data class Loaded(val products: List<ProductDto>) : MyProductsUiState
    data class Error(val message: String) : MyProductsUiState
}

/** Список товаров владельца — все статусы (сервер видит owner-токен). */
@HiltViewModel
class MyProductsViewModel @Inject constructor(
    private val repository: AdminProductsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    private val _uiState = MutableStateFlow<MyProductsUiState>(MyProductsUiState.Loading)
    val uiState: StateFlow<MyProductsUiState> = _uiState.asStateFlow()

    // load() вызывает экран (LaunchedEffect) — список перезагружается при возврате из формы.
    fun load() {
        _uiState.value = MyProductsUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.listProducts(storeId)) {
                is ApiResult.Success -> MyProductsUiState.Loaded(result.data.items)
                is ApiResult.ApiError -> MyProductsUiState.Error(result.message)
                is ApiResult.NetworkError -> MyProductsUiState.Error("Нет соединения с сервером")
            }
        }
    }
}
