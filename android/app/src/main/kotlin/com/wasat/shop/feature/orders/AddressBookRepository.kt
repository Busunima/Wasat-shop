package com.wasat.shop.feature.orders

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Адресная книга (FR-B11) в stores/{storeId}/customers/{uid}.addresses.
 * Rules: пишет только сам покупатель (как корзина FR-B04). Гость и
 * несконфигурированный Firebase — пустой список/no-op; сбои сети глотаются.
 */
@Singleton
class AddressBookRepository @Inject constructor(
    private val firestore: FirebaseFirestore?,
    private val auth: FirebaseAuth?,
) {
    private fun customerDoc(fs: FirebaseFirestore, storeId: String, uid: String) =
        fs.collection("stores").document(storeId).collection("customers").document(uid)

    suspend fun load(storeId: String): List<String> {
        val uid = auth?.currentUser?.uid ?: return emptyList()
        val fs = firestore ?: return emptyList()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            customerDoc(fs, storeId, uid).get().await().get("addresses") as? List<String>
        }.getOrNull() ?: emptyList()
    }

    /** Сохраняет адрес поверх существующих (правила слияния — AddressBook.withSaved). */
    suspend fun save(storeId: String, existing: List<String>, address: String) {
        val uid = auth?.currentUser?.uid ?: return
        val fs = firestore ?: return
        val merged = AddressBook.withSaved(existing, address)
        if (merged == existing) return
        runCatching {
            customerDoc(fs, storeId, uid)
                .set(mapOf("addresses" to merged), SetOptions.merge())
                .await()
        }
    }
}
