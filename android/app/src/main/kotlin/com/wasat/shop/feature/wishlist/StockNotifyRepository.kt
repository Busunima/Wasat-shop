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
 * Явная подписка «уведомить о поступлении» (ТЗ FR-B10): массив productId в
 * stores/{storeId}/customers/{uid}.stockNotifications. Одноразовая — сервер
 * снимает её после push о поступлении. Гость/без конфига Firebase — no-op.
 */
@Singleton
class StockNotifyRepository @Inject constructor(
    private val firestore: FirebaseFirestore?,
    private val auth: FirebaseAuth?,
    private val pushTokens: PushTokenRepository,
) {
    val isAvailable: Boolean
        get() = firestore != null && auth?.currentUser != null

    private fun customerDoc(fs: FirebaseFirestore, storeId: String, uid: String) =
        fs.collection("stores").document(storeId).collection("customers").document(uid)

    /** Реактивный набор productId с активной подпиской на поступление. */
    fun observe(storeId: String): Flow<Set<String>> {
        val fs = firestore ?: return flowOf(emptySet())
        val uid = auth?.currentUser?.uid ?: return flowOf(emptySet())
        return callbackFlow {
            val registration = customerDoc(fs, storeId, uid).addSnapshotListener { snap, _ ->
                @Suppress("UNCHECKED_CAST")
                val ids = (snap?.get("stockNotifications") as? List<String>)?.toSet() ?: emptySet()
                trySend(ids)
            }
            awaitClose { registration.remove() }
        }
    }

    /** Подписать/отписать; true — подписка теперь активна. */
    suspend fun toggle(storeId: String, productId: String, subscribed: Boolean): Boolean {
        val fs = firestore ?: return false
        val uid = auth?.currentUser?.uid ?: return false
        val update = if (subscribed) {
            FieldValue.arrayRemove(productId)
        } else {
            FieldValue.arrayUnion(productId)
        }
        runCatching {
            customerDoc(fs, storeId, uid)
                .set(mapOf("stockNotifications" to update), SetOptions.merge())
                .await()
        }
        // Подписка без токена бессмысленна — регистрируем FCM-токен (best-effort)
        if (!subscribed) pushTokens.register(storeId)
        return !subscribed
    }
}
