package com.wasat.shop.feature.orders

/**
 * Адресная книга покупателя (ТЗ §6 FR-B11): список сохранённых адресов доставки в
 * stores/{storeId}/customers/{uid}.addresses. Pure-логика слияния — под unit-тестом.
 */
object AddressBook {
    const val MAX_ADDRESSES = 10

    /**
     * Добавляет адрес в начало списка: трим, пустые игнорируются, дубликаты
     * (без учёта регистра/краевых пробелов) поднимаются наверх, размер ограничен.
     */
    fun withSaved(existing: List<String>, address: String): List<String> {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return existing
        val rest = existing.filterNot { it.trim().equals(trimmed, ignoreCase = true) }
        return (listOf(trimmed) + rest).take(MAX_ADDRESSES)
    }
}
