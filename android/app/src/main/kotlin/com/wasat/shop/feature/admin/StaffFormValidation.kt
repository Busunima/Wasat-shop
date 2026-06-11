package com.wasat.shop.feature.admin

/**
 * Валидация формы сотрудника (FR-A09) — зеркало серверной схемы
 * (server/src/schemas/staff.ts): email обязателен и ≤254. Pure JVM.
 */
object StaffFormValidation {
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    const val EMAIL_MAX = 254

    fun validateEmail(input: String): String? {
        val email = input.trim()
        return when {
            email.isEmpty() -> "Укажите email сотрудника"
            email.length > EMAIL_MAX -> "Email не длиннее $EMAIL_MAX символов"
            !EMAIL_REGEX.matches(email) -> "Некорректный email"
            else -> null
        }
    }
}
