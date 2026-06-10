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

enum class ProductField {
    NAME, PRICE, ORIGINAL_PRICE, DESCRIPTION, SKU, BARCODE, CATEGORY, TAGS, VARIANTS, IMAGES,
}

sealed interface SaveState {
    data object Idle : SaveState
    data object Loading : SaveState
    data class Failed(val message: String) : SaveState
    /** Сохранено или удалено — экран закрывается. */
    data object Done : SaveState
}

/** Черновик варианта в форме (stock — сырой ввод, парсится при сохранении). */
data class VariantDraft(
    val size: String = "",
    val color: String = "",
    val stockInput: String = "",
    val sku: String = "",
)

data class ProductEditUiState(
    /** null — создание нового товара; иначе — редактирование. */
    val productId: String? = null,
    val loadingExisting: Boolean = false,
    val name: String = "",
    val priceInput: String = "",
    val originalPriceInput: String = "",
    val description: String = "",
    val sku: String = "",
    val barcode: String = "",
    val category: String = "",
    val tagsInput: String = "",
    /** active / draft / archived (зеркало PRODUCT_STATUSES сервера). */
    val status: String = "draft",
    val images: List<String> = emptyList(),
    val uploadingImages: Int = 0,
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
        sku = sku.trim().ifEmpty { null },
    )
}

/**
 * Создание/редактирование/удаление товара владельцем (FR-A02 полный):
 * поля (вкл. SKU/штрихкод/старая цена/категория/теги), фото (мультизагрузка в Storage),
 * варианты с собственным sku, статус active/draft/archived.
 */
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
                    val p = result.data
                    it.copy(
                        loadingExisting = false,
                        name = p.name,
                        priceInput = minorToInput(p.price),
                        originalPriceInput = p.originalPrice?.let(::minorToInput) ?: "",
                        description = p.description,
                        sku = p.sku.orEmpty(),
                        barcode = p.barcode.orEmpty(),
                        category = p.category.orEmpty(),
                        tagsInput = TagsParser.format(p.tags),
                        status = p.status,
                        images = p.images,
                        variants = p.variants.map { v ->
                            VariantDraft(
                                size = v.size.orEmpty(),
                                color = v.color.orEmpty(),
                                stockInput = v.stock.toString(),
                                sku = v.sku.orEmpty(),
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

    private fun clearError(field: ProductField) = _uiState.update {
        it.copy(fieldErrors = it.fieldErrors - field)
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value) }
        clearError(ProductField.NAME)
    }

    fun onPriceChange(value: String) {
        _uiState.update { it.copy(priceInput = value) }
        clearError(ProductField.PRICE)
    }

    fun onOriginalPriceChange(value: String) {
        _uiState.update { it.copy(originalPriceInput = value) }
        clearError(ProductField.ORIGINAL_PRICE)
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value) }
        clearError(ProductField.DESCRIPTION)
    }

    fun onSkuChange(value: String) {
        _uiState.update { it.copy(sku = value) }
        clearError(ProductField.SKU)
    }

    fun onBarcodeChange(value: String) {
        _uiState.update { it.copy(barcode = value) }
        clearError(ProductField.BARCODE)
    }

    fun onCategoryChange(value: String) {
        _uiState.update { it.copy(category = value) }
        clearError(ProductField.CATEGORY)
    }

    fun onTagsChange(value: String) {
        _uiState.update { it.copy(tagsInput = value) }
        clearError(ProductField.TAGS)
    }

    fun onStatusChange(value: String) = _uiState.update { it.copy(status = value) }

    // --- Фото (мультизагрузка, FR-A02) ---

    fun addImages(uris: List<Pair<Uri, String?>>) {
        if (uris.isEmpty()) return
        val s = _uiState.value
        if (s.uploadingImages > 0) return

        val freeSlots = ProductFormValidation.IMAGES_MAX - s.images.size
        if (freeSlots <= 0) {
            _uiState.update {
                it.copy(
                    fieldErrors = it.fieldErrors +
                        (ProductField.IMAGES to "Не больше ${ProductFormValidation.IMAGES_MAX} фото"),
                )
            }
            return
        }
        val toUpload = uris.take(freeSlots)

        _uiState.update {
            it.copy(uploadingImages = toUpload.size, fieldErrors = it.fieldErrors - ProductField.IMAGES)
        }
        viewModelScope.launch {
            var failure: String? = null
            for ((uri, contentType) in toUpload) {
                imageRepository.uploadProductImage(storeId, uri, contentType)
                    .onSuccess { url ->
                        _uiState.update {
                            it.copy(images = it.images + url, uploadingImages = it.uploadingImages - 1)
                        }
                    }
                    .onFailure { e ->
                        failure = e.message ?: "Не удалось загрузить фото"
                        _uiState.update { it.copy(uploadingImages = it.uploadingImages - 1) }
                    }
            }
            failure?.let { msg ->
                _uiState.update { it.copy(fieldErrors = it.fieldErrors + (ProductField.IMAGES to msg)) }
            }
        }
    }

    fun removeImage(url: String) = _uiState.update { it.copy(images = it.images - url) }

    // --- Варианты ---

    fun addVariant() = _uiState.update { it.copy(variants = it.variants + VariantDraft()) }

    fun removeVariant(index: Int) {
        _uiState.update { it.copy(variants = it.variants.filterIndexed { i, _ -> i != index }) }
        clearError(ProductField.VARIANTS)
    }

    fun onVariantChange(index: Int, draft: VariantDraft) {
        _uiState.update {
            it.copy(variants = it.variants.mapIndexed { i, v -> if (i == index) draft else v })
        }
        clearError(ProductField.VARIANTS)
    }

    // --- Сохранение / удаление ---

    fun save() {
        val s = _uiState.value
        if (s.save is SaveState.Loading || s.loadingExisting || s.uploadingImages > 0) return

        val errors = buildMap {
            ProductFormValidation.validateName(s.name)?.let { put(ProductField.NAME, it) }
            ProductFormValidation.validatePrice(s.priceInput, currency)?.let { put(ProductField.PRICE, it) }
            ProductFormValidation.validateOptionalPrice(s.originalPriceInput, currency)
                ?.let { put(ProductField.ORIGINAL_PRICE, it) }
            ProductFormValidation.validateDescription(s.description)?.let { put(ProductField.DESCRIPTION, it) }
            ProductFormValidation.validateSku(s.sku)?.let { put(ProductField.SKU, it) }
            ProductFormValidation.validateSku(s.barcode)?.let { put(ProductField.BARCODE, it) }
            ProductFormValidation.validateCategory(s.category)?.let { put(ProductField.CATEGORY, it) }
            TagsParser.validate(s.tagsInput)?.let { put(ProductField.TAGS, it) }
            s.variants.firstNotNullOfOrNull { ProductFormValidation.validateStock(it.stockInput) }
                ?.let { put(ProductField.VARIANTS, it) }
            s.variants.firstNotNullOfOrNull { ProductFormValidation.validateSku(it.sku) }
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
            status = s.status,
            images = s.images,
            variants = s.variants.mapNotNull { it.toVariantDto() },
            originalPrice = s.originalPriceInput.takeIf { it.isNotBlank() }
                ?.let { PriceParser.parse(it, currency) },
            sku = s.sku.trim(),
            barcode = s.barcode.trim(),
            category = s.category.trim(),
            tags = TagsParser.parse(s.tagsInput),
        )

        _uiState.update { it.copy(save = SaveState.Loading) }
        viewModelScope.launch {
            val result = if (s.productId == null) {
                repository.create(storeId, body)
            } else {
                repository.update(storeId, s.productId, body)
            }
            _uiState.update { it.copy(save = result.toSaveState()) }
        }
    }

    fun delete() {
        val s = _uiState.value
        val productId = s.productId ?: return
        if (s.save is SaveState.Loading) return

        _uiState.update { it.copy(save = SaveState.Loading) }
        viewModelScope.launch {
            _uiState.update { it.copy(save = repository.delete(storeId, productId).toSaveState()) }
        }
    }

    private fun <T> ApiResult<T>.toSaveState(): SaveState = when (this) {
        is ApiResult.Success -> SaveState.Done
        is ApiResult.ApiError -> SaveState.Failed(message)
        is ApiResult.NetworkError -> SaveState.Failed("Нет соединения с сервером")
    }
}
