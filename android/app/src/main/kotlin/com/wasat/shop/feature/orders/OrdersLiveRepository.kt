package com.wasat.shop.feature.orders

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Live-обновление заказов покупателя (ТЗ §6 FR-B06): snapshot-listener на свои
 * заказы (Rules разрешают чтение customerUid == uid). Каждый снапшот — сигнал
 * перезагрузить список через REST (канонический контракт), без клиентского
 * маппинга Firestore-документов. Гость/без конфига Firebase — пустой поток.
 */
@Singleton
class OrdersLiveRepository @Inject constructor(
    private val firestore: FirebaseFirestore?,
    private val auth: FirebaseAuth?,
) {
    fun observeMyOrders(storeId: String): Flow<Unit> {
        val fs = firestore ?: return emptyFlow()
        val uid = auth?.currentUser?.uid ?: return emptyFlow()
        return callbackFlow {
            val registration = fs.collection("stores").document(storeId)
                .collection("orders")
                .whereEqualTo("customerUid", uid)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) trySend(Unit)
                }
            awaitClose { registration.remove() }
        }
    }
}
