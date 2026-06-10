package com.wasat.shop.feature.admin

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Загрузка фото товара в Firebase Storage (ТЗ §13: путь stores/{storeId}/... —
 * запись разрешена правилами только владельцу/сотруднику, contentType image, ≤10 МБ).
 * Возвращает публичный downloadUrl — он сохраняется в products.images.
 */
@Singleton
class ProductImageRepository @Inject constructor(
    private val storage: FirebaseStorage?,
) {
    suspend fun uploadProductImage(
        storeId: String,
        uri: Uri,
        contentType: String?,
    ): Result<String> = runCatching {
        val storage = checkNotNull(storage) { "Firebase не сконфигурирован" }
        val ref = storage.reference.child("stores/$storeId/products/${UUID.randomUUID()}")
        val metadata = StorageMetadata.Builder()
            .setContentType(contentType ?: "image/jpeg")
            .build()
        ref.putFile(uri, metadata).await()
        ref.downloadUrl.await().toString()
    }
}
