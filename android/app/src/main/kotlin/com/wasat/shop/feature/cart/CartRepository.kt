package com.wasat.shop.feature.cart

import com.wasat.shop.core.db.CartDao
import com.wasat.shop.core.db.CartItemEntity
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.VariantDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Локальная корзина на Room (FR-B04, офлайн-first). Гостевая корзина живёт на
 * устройстве; слияние с серверной — вместе с чекаутом (Фаза 4).
 */
@Singleton
class CartRepository @Inject constructor(
    private val dao: CartDao,
) {
    fun observeCart(storeId: String): Flow<List<CartItemEntity>> = dao.observeCart(storeId)

    fun observeCount(storeId: String): Flow<Int> = dao.observeCount(storeId)

    /** Добавляет товар (или увеличивает количество существующей позиции). */
    suspend fun add(storeId: String, currency: String, product: ProductDto, variant: VariantDto?) {
        val key = CartTotals.variantKey(variant)
        val existing = dao.find(storeId, product.id, key)
        if (existing != null) {
            dao.updateQuantity(
                storeId,
                product.id,
                key,
                CartTotals.clampQuantity(existing.quantity + 1),
            )
        } else {
            dao.upsert(
                CartItemEntity(
                    storeId = storeId,
                    productId = product.id,
                    variantKey = key,
                    name = product.name,
                    price = product.price,
                    currency = currency,
                    imageUrl = product.images.firstOrNull(),
                    quantity = 1,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Меняет количество; qty <= 0 удаляет позицию. */
    suspend fun setQuantity(item: CartItemEntity, quantity: Int) {
        if (quantity <= 0) {
            dao.delete(item.storeId, item.productId, item.variantKey)
        } else {
            dao.updateQuantity(
                item.storeId,
                item.productId,
                item.variantKey,
                CartTotals.clampQuantity(quantity),
            )
        }
    }

    suspend fun remove(item: CartItemEntity) = dao.delete(item.storeId, item.productId, item.variantKey)

    suspend fun clear(storeId: String) = dao.clear(storeId)
}
