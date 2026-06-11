package com.wasat.shop.feature.wishlist

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.wasat.shop.core.push.PushTokenRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * Вишлист (ТЗ FR-B07): массив productId в stores/{storeId}/customers/{uid}.wishlist.
 * Требует авторизации (Rules: пишет только сам покупатель); гость/без конфига
 * Firebase — пустой поток, toggle — no-op.
 */
@Singleton
class WishlistRepository @Inject constructor(
    private val firestore: FirebaseFirestore?,
    private val auth: FirebaseAuth?,
    private val pushTokens: PushTokenRepository,
) {
    val isAvailable: Boolean
        get() = firestore != null && auth?.currentUser != null

    private fun customerDoc(fs: FirebaseFirestore, storeId: String, uid: String) =
        fs.collection("stores").document(storeId).collection("customers").document(uid)

    /** Реактивный набор productId в вишлисте магазина. */
    fun observe(storeId: String): Flow<Set<String>> {
        val fs = firestore ?: return flowOf(emptySet())
        val uid = auth?.currentUser?.uid ?: return flowOf(emptySet())
        return callbackFlow {
            val registration = customerDoc(fs, storeId, uid).addSnapshotListener { snap, _ ->
                @Suppress("UNCHECKED_CAST")
                val ids = (snap?.get("wishlist") as? List<String>)?.toSet() ?: emptySet()
                trySend(ids)
            }
            awaitClose { registration.remove() }
        }
    }

    /** Добавить/убрать товар; true — товар теперь в вишлисте. */
    suspend fun toggle(storeId: String, productId: String, inWishlist: Boolean): Boolean {
        val fs = firestore ?: return false
        val uid = auth?.currentUser?.uid ?: return false
        val update = if (inWishlist) {
            FieldValue.arrayRemove(productId)
        } else {
            FieldValue.arrayUnion(productId)
        }
        runCatching {
            customerDoc(fs, storeId, uid)
                .set(mapOf("wishlist" to update), SetOptions.merge())
                .await()
        }
        // FR-B10: добавление в вишлист = интерес к товару → регистрируем FCM-токен
        // (best-effort), чтобы получать push о наличии/снижении цены.
        if (!inWishlist) pushTokens.register(storeId)
        return !inWishlist
    }
}
