package com.wasat.shop.feature.admin

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.dto.ProductUpsertRequest
import com.wasat.shop.core.network.dto.VariantDto
import com.wasat.shop.core.util.PriceParser
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.util.Currency
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProductField { NAME, PRICE, DESCRIPTION, VARIANTS, IMAGES }

sealed interface SaveState {
    data object Idle : SaveState
    data object Loading : SaveState
    data class Failed(val message: String) : SaveState
    data object Saved : SaveState
}

/** Черновик варианта в форме (stock — сырой ввод, парсится при сохранении). */
data class VariantDraft(
    val size: String = "",
    val color: String = "",
    val stockInput: String = "",
)

data class ProductEditUiState(
    /** null — создание нового товара; иначе — редактирование. */
    val productId: String? = null,
    val loadingExisting: Boolean = false,
    val name: String = "",
    val priceInput: String = "",
    val description: String = "",
    val isActive: Boolean = false,
    val images: List<String> = emptyList(),
    val uploadingImage: Boolean = false,
    val variants: List<VariantDraft> = emptyList(),
    val fieldErrors: Map<ProductField, String> = emptyMap(),
    val save: SaveState = SaveState.Idle,
)

/** Маппинг черновика в DTO; null — невалидный stock (pure JVM, под тестом). */
fun VariantDraft.toVariantDto(): VariantDto? {
    val stock = ProductFormValidation.parseStock(stockInput) ?: return null
    return VariantDto(
        size = size.trim().ifEmpty { null },
        color = color.trim().ifEmpty { null },
        stock = stock,
    )
}

/** Создание/редактирование товара владельцем (FR-A02): поля, фото (Storage), варианты. */
@HiltViewModel
class ProductEditViewModel @Inject constructor(
    private val repository: AdminProductsRepository,
    private val imageRepository: ProductImageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])
    private val currency: String = savedStateHandle["currency"] ?: "USD"
    private val initialProductId: String? = savedStateHandle["productId"]

    private val _uiState = MutableStateFlow(
        ProductEditUiState(productId = initialProductId, loadingExisting = initialProductId != null),
    )
    val uiState: StateFlow<ProductEditUiState> = _uiState.asStateFlow()

    init {
        initialProductId?.let { loadExisting(it) }
    }

    private fun loadExisting(productId: String) {
        viewModelScope.launch {
            when (val result = repository.getProduct(storeId, productId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        loadingExisting = false,
                        name = result.data.name,
                        priceInput = minorToInput(result.data.price),
                        description = result.data.description,
                        isActive = result.data.status == "active",
                        images = result.data.images,
                        variants = result.data.variants.map { v ->
                            VariantDraft(
                                size = v.size.orEmpty(),
                                color = v.color.orEmpty(),
                                stockInput = v.stock.toString(),
                            )
                        },
                    )
                }
                else -> _uiState.update {
                    it.copy(loadingExisting = false, save = SaveState.Failed("Не удалось загрузить товар"))
                }
            }
        }
    }

    /** Минорные единицы → строка для поля ввода ("12990" → "129.90" для USD). */
    private fun minorToInput(minor: Long): String {
        val digits = runCatching { Currency.getInstance(currency).defaultFractionDigits }
            .getOrNull() ?: 2
        return BigDecimal.valueOf(minor).movePointLeft(digits).toPlainString()
    }

    fun onNameChange(value: String) = _uiState.update {
        it.copy(name = value, fieldErrors = it.fieldErrors - ProductField.NAME)
    }

    fun onPriceChange(value: String) = _uiState.update {
        it.copy(priceInput = value, fieldErrors = it.fieldErrors - ProductField.PRICE)
    }

    fun onDescriptionChange(value: String) = _uiState.update {
        it.copy(description = value, fieldErrors = it.fieldErrors - ProductField.DESCRIPTION)
    }

    fun onActiveChange(value: Boolean) = _uiState.update { it.copy(isActive = value) }

    // --- Фото ---

    fun addImage(uri: Uri, contentType: String?) {
        val s = _uiState.value
        if (s.uploadingImage) return
        if (s.images.size >= ProductFormValidation.IMAGES_MAX) {
            _uiState.update {
                it.copy(fieldErrors = it.fieldErrors + (ProductField.IMAGES to "Не больше ${ProductFormValidation.IMAGES_MAX} фото"))
            }
            return
        }
        _uiState.update {
            it.copy(uploadingImage = true, fieldErrors = it.fieldErrors - ProductField.IMAGES)
        }
        viewModelScope.launch {
            imageRepository.uploadProductImage(storeId, uri, contentType)
                .onSuccess { url ->
                    _uiState.update { it.copy(uploadingImage = false, images = it.images + url) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            uploadingImage = false,
                            fieldErrors = it.fieldErrors +
                                (ProductField.IMAGES to (e.message ?: "Не удалось загрузить фото")),
                        )
                    }
                }
        }
    }

    fun removeImage(url: String) = _uiState.update { it.copy(images = it.images - url) }

    // --- Варианты ---

    fun addVariant() = _uiState.update { it.copy(variants = it.variants + VariantDraft()) }

    fun removeVariant(index: Int) = _uiState.update {
        it.copy(
            variants = it.variants.filterIndexed { i, _ -> i != index },
            fieldErrors = it.fieldErrors - ProductField.VARIANTS,
        )
    }

    fun onVariantChange(index: Int, draft: VariantDraft) = _uiState.update {
        it.copy(
            variants = it.variants.mapIndexed { i, v -> if (i == index) draft else v },
            fieldErrors = it.fieldErrors - ProductField.VARIANTS,
        )
    }

    fun save() {
        val s = _uiState.value
        if (s.save is SaveState.Loading || s.loadingExisting || s.uploadingImage) return

        val errors = buildMap {
            ProductFormValidation.validateName(s.name)?.let { put(ProductField.NAME, it) }
            ProductFormValidation.validatePrice(s.priceInput, currency)?.let { put(ProductField.PRICE, it) }
            ProductFormValidation.validateDescription(s.description)?.let { put(ProductField.DESCRIPTION, it) }
            s.variants.firstNotNullOfOrNull { ProductFormValidation.validateStock(it.stockInput) }
                ?.let { put(ProductField.VARIANTS, it) }
        }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(fieldErrors = errors) }
            return
        }

        val body = ProductUpsertRequest(
            name = s.name.trim(),
            price = checkNotNull(PriceParser.parse(s.priceInput, currency)),
            description = s.description.trim(),
            status = if (s.isActive) "active" else "draft",
            images = s.images,
            variants = s.variants.mapNotNull { it.toVariantDto() },
        )

        _uiState.update { it.copy(save = SaveState.Loading) }
        viewModelScope.launch {
            val result = if (s.productId == null) {
                repository.create(storeId, body)
            } else {
                repository.update(storeId, s.productId, body)
            }
            _uiState.update {
                it.copy(
                    save = when (result) {
                        is ApiResult.Success -> SaveState.Saved
                        is ApiResult.ApiError -> SaveState.Failed(result.message)
                        is ApiResult.NetworkError -> SaveState.Failed("Нет соединения с сервером")
                    },
                )
            }
        }
    }
}
