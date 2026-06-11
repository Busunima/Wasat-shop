package com.wasat.shop.feature.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.StaffInviteRequest
import com.wasat.shop.core.network.dto.StaffMemberDto
import com.wasat.shop.core.network.dto.StaffRoleUpdateRequest
import com.wasat.shop.core.network.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class StaffUiState(
    val loading: Boolean = true,
    val members: List<StaffMemberDto> = emptyList(),
    val email: String = "",
    val role: String = "staff",
    val emailError: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/** Сотрудники магазина (FR-A09): список, приглашение по email, смена роли, удаление. */
@HiltViewModel
class StaffViewModel @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val storeId: String = checkNotNull(savedStateHandle["storeId"])

    private val _uiState = MutableStateFlow(StaffUiState())
    val uiState: StateFlow<StaffUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _uiState.update {
                when (val r = safeApiCall(json) { api.listStaff(storeId) }) {
                    is ApiResult.Success -> it.copy(loading = false, members = r.data.items)
                    is ApiResult.ApiError -> it.copy(loading = false, error = r.message)
                    is ApiResult.NetworkError ->
                        it.copy(loading = false, error = "Нет соединения с сервером")
                }
            }
        }
    }

    fun onEmail(v: String) = _uiState.update { it.copy(email = v, emailError = null) }
    fun onRole(v: String) = _uiState.update { it.copy(role = v) }

    fun invite() {
        val email = _uiState.value.email.trim()
        val emailError = StaffFormValidation.validateEmail(email)
        if (emailError != null) {
            _uiState.update { it.copy(emailError = emailError) }
            return
        }
        if (_uiState.value.busy) return

        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val body = StaffInviteRequest(email = email, role = _uiState.value.role)
            when (val r = safeApiCall(json) { api.addStaff(storeId, body) }) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(busy = false, email = "") }
                    load()
                }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    fun changeRole(uid: String, role: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = safeApiCall(json) { api.updateStaffRole(storeId, uid, StaffRoleUpdateRequest(role)) }) {
                is ApiResult.Success ->
                    _uiState.update { s ->
                        s.copy(busy = false, members = s.members.map { if (it.uid == uid) r.data else it })
                    }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }

    fun remove(uid: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val r = safeApiCall(json) { api.removeStaff(storeId, uid) }) {
                is ApiResult.Success ->
                    _uiState.update { s -> s.copy(busy = false, members = s.members.filter { it.uid != uid }) }
                is ApiResult.ApiError ->
                    _uiState.update { it.copy(busy = false, error = r.message) }
                is ApiResult.NetworkError ->
                    _uiState.update { it.copy(busy = false, error = "Нет соединения с сервером") }
            }
        }
    }
}
