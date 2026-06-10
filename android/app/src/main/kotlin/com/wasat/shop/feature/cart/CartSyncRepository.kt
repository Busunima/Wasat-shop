package com.wasat.shop.feature.cart

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.wasat.shop.core.db.CartDao
import com.wasat.shop.core.db.CartItemEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Синхронизация корзины с Firestore (ТЗ FR-B04): снапшот корзины магазина хранится
 * в stores/{storeId}/customers/{uid}.cart (Rules: пишет только сам покупатель).
 * Гость и несконфигурированный Firebase — no-op (корзина остаётся локальной);
 * сетевые сбои глотаются: локальная корзина — источник истины до чекаута.
 */
@Singleton
class CartSyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore?,
    private val auth: FirebaseAuth?,
    private val dao: CartDao,
) {
    private fun customerDoc(fs: FirebaseFirestore, storeId: String, uid: String) =
        fs.collection("stores").document(storeId).collection("customers").document(uid)

    /** Пуш текущего снапшота корзины магазина (после каждой мутации). */
    suspend fun push(storeId: String) {
        val uid = auth?.currentUser?.uid ?: return
        val fs = firestore ?: return
        val items = dao.getAll(storeId)
        runCatching {
            customerDoc(fs, storeId, uid)
                .set(mapOf("cart" to items.map(::toMap)), SetOptions.merge())
                .await()
        }
    }

    /**
     * Слияние гостевой и серверной корзины при входе (правило — CartMerge):
     * по каждому магазину с локальной корзиной читаем серверную, сливаем,
     * пишем результат в Room и обратно в Firestore.
     */
    suspend fun mergeOnSignIn() {
        val uid = auth?.currentUser?.uid ?: return
        val fs = firestore ?: return
        for (storeId in dao.getStoreIds()) {
            runCatching {
                val snapshot = customerDoc(fs, storeId, uid).get().await()
                val merged = CartMerge.merge(
                    local = dao.getAll(storeId),
                    remote = parseCart(snapshot, storeId),
                )
                dao.replaceAll(storeId, merged)
                customerDoc(fs, storeId, uid)
                    .set(mapOf("cart" to merged.map(::toMap)), SetOptions.merge())
                    .await()
            }
        }
    }

    private fun toMap(item: CartItemEntity): Map<String, Any?> = mapOf(
        "productId" to item.productId,
        "variantKey" to item.variantKey,
        "name" to item.name,
        "price" to item.price,
        "currency" to item.currency,
        "imageUrl" to item.imageUrl,
        "quantity" to item.quantity,
        "addedAt" to item.addedAt,
    )

    private fun parseCart(snapshot: DocumentSnapshot, storeId: String): List<CartItemEntity> {
        @Suppress("UNCHECKED_CAST")
        val raw = snapshot.get("cart") as? List<Map<String, Any?>> ?: return emptyList()
        return raw.mapNotNull { entry ->
            val productId = entry["productId"] as? String ?: return@mapNotNull null
            CartItemEntity(
                storeId = storeId,
                productId = productId,
                variantKey = entry["variantKey"] as? String ?: "",
                name = entry["name"] as? String ?: "",
                price = (entry["price"] as? Number)?.toLong() ?: 0L,
                currency = entry["currency"] as? String ?: "USD",
                imageUrl = entry["imageUrl"] as? String,
                quantity = CartTotals.clampQuantity((entry["quantity"] as? Number)?.toInt() ?: 1),
                addedAt = (entry["addedAt"] as? Number)?.toLong() ?: 0L,
            )
        }
    }
}
