package com.wasat.shop.feature.admin

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Загрузка брендинга магазина (логотип/баннер, FR-A01) в Storage:
 * путь stores/{storeId}/branding/... — Rules разрешают запись владельцу/сотруднику.
 */
@Singleton
class StoreImageRepository @Inject constructor(
    private val storage: FirebaseStorage?,
) {
    suspend fun uploadBrandingImage(
        storeId: String,
        uri: Uri,
        contentType: String?,
    ): Result<String> = runCatching {
        val storage = checkNotNull(storage) { "Firebase не сконфигурирован" }
        val ref = storage.reference.child("stores/$storeId/branding/${UUID.randomUUID()}")
        val metadata = StorageMetadata.Builder()
            .setContentType(contentType ?: "image/jpeg")
            .build()
        ref.putFile(uri, metadata).await()
        ref.downloadUrl.await().toString()
    }
}
