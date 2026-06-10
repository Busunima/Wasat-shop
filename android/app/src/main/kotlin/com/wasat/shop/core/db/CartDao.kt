package com.wasat.shop.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {
    /** Корзина ведётся помагазинно (мульти-стор платформа). */
    @Query("SELECT * FROM cart_items WHERE storeId = :storeId ORDER BY addedAt")
    fun observeCart(storeId: String): Flow<List<CartItemEntity>>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM cart_items WHERE storeId = :storeId")
    fun observeCount(storeId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CartItemEntity)

    @Query(
        "SELECT * FROM cart_items WHERE storeId = :storeId AND productId = :productId AND variantKey = :variantKey",
    )
    suspend fun find(storeId: String, productId: String, variantKey: String): CartItemEntity?

    @Query(
        "UPDATE cart_items SET quantity = :quantity " +
            "WHERE storeId = :storeId AND productId = :productId AND variantKey = :variantKey",
    )
    suspend fun updateQuantity(storeId: String, productId: String, variantKey: String, quantity: Int)

    @Query(
        "DELETE FROM cart_items " +
            "WHERE storeId = :storeId AND productId = :productId AND variantKey = :variantKey",
    )
    suspend fun delete(storeId: String, productId: String, variantKey: String)

    @Query("DELETE FROM cart_items WHERE storeId = :storeId")
    suspend fun clear(storeId: String)

    // ── Синхронизация с Firestore (FR-B04) ──────────────────────────────────

    @Query("SELECT * FROM cart_items WHERE storeId = :storeId ORDER BY addedAt")
    suspend fun getAll(storeId: String): List<CartItemEntity>

    /** Магазины, в которых есть локальная (в т.ч. гостевая) корзина. */
    @Query("SELECT DISTINCT storeId FROM cart_items")
    suspend fun getStoreIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CartItemEntity>)

    /** Атомарная замена корзины магазина результатом слияния. */
    @Transaction
    suspend fun replaceAll(storeId: String, items: List<CartItemEntity>) {
        clear(storeId)
        upsertAll(items)
    }
}
