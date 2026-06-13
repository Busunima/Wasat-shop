package com.wasat.shop.feature.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.CategoryCreateRequest
import com.wasat.shop.core.network.dto.CategoryDto
import com.wasat.shop.core.network.dto.CategoryUpdateRequest
import com.wasat.shop.core.network.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Валидация категории (зеркало categoryCreateSchema). Pure JVM — под unit-тестом. */
object CategoryValidation {
    private val SLUG = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

    fun validateName(v: String): String? = when {
        v.isBlank() -> "Введите название"
        v.trim().length > 80 -> "До 80 символов"
        else -> null
    }

    fun validateSlug(v: String): String? = when {
        v.length !in 2..40 -> "Slug 2–40 символов"
        !SLUG.matches(v) -> "Латиница, цифры и дефис"
        else -> null
    }
}

/** Форма создания/редактирования категории; editingId=null — режим создания. */
data class CategoryForm(
    val editingId: String? = null,
    val name: String = "",
    val slug: String = "",
    /** null — категория верхнего уровня (корень). */
    val parentId: String? = null,
    val order: String = "0",
    val imageUrl: String = "",
    val nameError: String? = null,
    val slugError: String? = null,
)

data class CategoriesUiState(
    val loading: Boolean = true,
    val categories: List<CategoryDto> = emptyList(),
    val form: CategoryForm = CategoryForm(),
    val busy: Boolean = false,
    val error: String? = null,
)

/** Категории магазина (FR-A01): список-дерево, создание, правка, удаление. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _uiState.update {
                when (val r = safeApiCall(json) { api.listCategories(storeId) }) {
                    is ApiResult.Success -> it.copy(loading = false, categories = r.data.items)
                    is ApiResult.ApiError -> it.copy(loading = false, error = r.message)
                    is ApiResult.NetworkError ->
                        it.copy(loading = false, error = "Нет соединения с сервером")
                }
            }
        }
    }

    fun onName(v: String) = _uiState.update { it.copy(form = it.form.copy(name = v, nameError = null)) }
    fun onSlug(v: String) = _uiState.update { it.copy(form = it.form.copy(slug = v, slugError = null)) }
    fun onParent(id: String?) = _uiState.update { it.copy(form = it.form.copy(parentId = id)) }
    fun onOrder(v: String) =
        _uiState.update { it.copy(form = it.form.copy(order = v.filter(Char::isDigit))) }

    /** Загрузить категорию в форму для редактирования. */
    fun startEdit(c: CategoryDto) = _uiState.update {
        it.copy(
            form = CategoryForm(
                editingId = c.id,
                name = c.name,
                slug = c.slug,
                parentId = c.parentId,
                order = c.order.toString(),
                imageUrl = c.imageUrl.orEmpty(),
            ),
        )
    }

    fun cancelEdit() = _uiState.update { it.copy(form = CategoryForm()) }

    /** Валидация (зеркало схемы) → POST (создание) или PATCH (правка) → перезагрузка. */
    fun save() {
        val form = _uiState.value.form
        val nameError = CategoryValidation.validateName(form.name)
        val slugError = CategoryValidation.validateSlug(form.slug)
        if (nameError != null || slugError != null) {
            _uiState.update {
                it.copy(form = form.copy(nameError = nameError, slugError = slugError))
            }
            return
        }
        if (_uiState.value.busy) return

        val order = form.order.toIntOrNull() ?: 0
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = if (form.editingId == null) {
                safeApiCall(json) {
                    api.createCategory(
                        storeId,
                        CategoryCreateRequest(
                            name = form.name.trim(),
                            slug = form.slug.trim(),
                            parentId = form.parentId,
                            order = order,
                            imageUrl = form.imageUrl.ifBlank { null },
                        ),
                    )
                }
            } else {
                safeApiCall(json) {
                    api.updateCategory(
                        storeId,
                        form.editingId,
                        CategoryUpdateRequest(
                            name = form.name.trim(),
                            slug = form.slug.trim(),
                            parentId = form.parentId.orEmpty(), // "" → корень
                            order = order,
                            imageUrl = form.imageUrl,
                        ),
                    )
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(busy = false, form = CategoryForm()) }
                    load()
                }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = result.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    fun delete(id: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = safeApiCall(json) { api.deleteCategory(storeId, id) }) {
                is ApiResult.Success -> {
                    _uiState.update { s ->
                        s.copy(
                            busy = false,
                            categories = s.categories.filter { it.id != id },
                            form = if (s.form.editingId == id) CategoryForm() else s.form,
                        )
                    }
                }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
