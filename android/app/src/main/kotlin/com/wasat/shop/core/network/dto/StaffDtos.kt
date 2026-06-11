package com.wasat.shop.core.network.dto

import kotlinx.serialization.Serializable

/** DTO сотрудников — контракт /api/stores/{id}/staff (FR-A09). */

@Serializable
data class StaffMemberDto(
    val uid: String,
    val email: String = "",
    /** manager | staff */
    val role: String = "staff",
    val addedAt: Long? = null,
)

@Serializable
data class StaffListResponse(val items: List<StaffMemberDto> = emptyList())

@Serializable
data class StaffInviteRequest(
    val email: String,
    val role: String,
)

@Serializable
data class StaffRoleUpdateRequest(val role: String)
