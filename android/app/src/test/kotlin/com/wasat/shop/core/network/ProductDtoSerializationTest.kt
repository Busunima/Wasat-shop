package com.wasat.shop.core.network

import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.ProductListResponse
import com.wasat.shop.core.network.dto.StoreInfoDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Сериализация DTO товаров/магазина ↔ контракт сервера. */
class ProductDtoSerializationTest {

    private val json = Json { ignoreUnknownKeys = true } // как в NetworkModule

    @Test
    fun `товар декодируется с дефолтами для опциональных полей`() {
        val payload = """{"id":"p1","name":"Кеды","price":9990,"status":"active"}"""
        val product = json.decodeFromString(ProductDto.serializer(), payload)
        assertEquals(9990L, product.price)
        assertEquals(emptyList<String>(), product.images)
        assertEquals(0, product.totalStock)
        assertNull(product.originalPrice)
    }

    @Test
    fun `листинг с вариантами и неизвестными полями`() {
        val payload = """
            {"items":[{"id":"p1","name":"Футболка","price":1990,"status":"active",
              "variants":[{"size":"M","stock":3},{"size":"L","stock":0,"sku":"TS-L"}],
              "totalStock":3,"rating":4.5,"futureField":true}]}
        """.trimIndent()
        val list = json.decodeFromString(ProductListResponse.serializer(), payload)
        assertEquals(1, list.items.size)
        assertEquals(2, list.items[0].variants.size)
        assertEquals("TS-L", list.items[0].variants[1].sku)
    }

    @Test
    fun `карточка магазина декодируется`() {
        val payload = """
            {"storeId":"s1","slug":"my-shop","name":"My Shop","description":"",
             "currency":"USD","isPublic":false}
        """.trimIndent()
        val store = json.decodeFromString(StoreInfoDto.serializer(), payload)
        assertEquals("USD", store.currency)
        assertEquals(false, store.isPublic)
    }
}
