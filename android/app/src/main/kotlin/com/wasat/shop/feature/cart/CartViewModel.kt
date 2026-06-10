package com.wasat.shop.feature.cart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.db.CartItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CartUiState(
    val items: List<CartItemEntity> = emptyList(),
    val subtotal: Long = 0,
    val itemCount: Int = 0,
)

@HiltViewModel
class CartViewModel @Inject constructor(
    private val repository: CartRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    val uiState: StateFlow<CartUiState> = repository.observeCart(storeId)
        .map { items ->
            CartUiState(
                items = items,
                subtotal = CartTotals.subtotal(items),
                itemCount = CartTotals.itemCount(items),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartUiState())

    fun increment(item: CartItemEntity) {
        viewModelScope.launch { repository.setQuantity(item, item.quantity + 1) }
    }

    fun decrement(item: CartItemEntity) {
        viewModelScope.launch { repository.setQuantity(item, item.quantity - 1) }
    }

    fun remove(item: CartItemEntity) {
        viewModelScope.launch { repository.remove(item) }
    }
}
