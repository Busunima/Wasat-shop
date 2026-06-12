package com.wasat.shop.feature.orders

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressBookTest {

    @Test
    fun `withSaved - новый адрес в начало списка`() {
        assertEquals(
            listOf("ул. Мира, 5", "ул. Ленина, 1"),
            AddressBook.withSaved(listOf("ул. Ленина, 1"), "ул. Мира, 5"),
        )
    }

    @Test
    fun `withSaved - трим и игнор пустого`() {
        assertEquals(listOf("адрес"), AddressBook.withSaved(emptyList(), "  адрес  "))
        assertEquals(listOf("x"), AddressBook.withSaved(listOf("x"), "   "))
    }

    @Test
    fun `withSaved - дубликат поднимается наверх без удвоения`() {
        assertEquals(
            listOf("ул. Мира, 5", "ул. Ленина, 1"),
            AddressBook.withSaved(listOf("ул. Ленина, 1", "УЛ. МИРА, 5"), "ул. Мира, 5"),
        )
    }

    @Test
    fun `withSaved - размер ограничен`() {
        val full = (1..AddressBook.MAX_ADDRESSES).map { "адрес $it" }
        val result = AddressBook.withSaved(full, "новый")
        assertEquals(AddressBook.MAX_ADDRESSES, result.size)
        assertEquals("новый", result.first())
    }
}
