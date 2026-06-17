package com.wasat.shop.feature.admin

import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.StockAdjustRequest
import com.wasat.shop.core.network.dto.StockResultDto
import com.wasat.shop.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Инвентарь (FR-A03) с офлайн-кэшем (B5.3): список товаров кэшируется для
 * просмотра остатков без сети; корректировка остатка идёт через outbox
 * (см. OutboxRepository.enqueueStockAdjust → adjustStock здесь).
 */
@Singleton
class InventoryRepository @Inject constructor(
    private val api: WasatApi,
    private val adminProducts: AdminProductsRepository,
    private val cache: InventoryCache,
    private val json: Json,
) {
    /** Сетевой список товаров; при успехе кэшируется. */
    suspend fun refreshProducts(storeId: String): ApiResult<List<ProductDto>> =
        when (val r = adminProducts.listProducts(storeId)) {
            is ApiResult.Success -> {
                cache.save(storeId, r.data.items)
                ApiResult.Success(r.data.items)
            }
            is ApiResult.ApiError -> r
            is ApiResult.NetworkError -> r
        }

    suspend fun cachedProducts(storeId: String): List<ProductDto> = cache.load(storeId)

    /** Оптимистично применить дельту к кэшу; вернуть обновлённый список для UI. */
    suspend fun optimisticAdjust(
        storeId: String,
        productId: String,
        sku: String?,
        size: String?,
        color: String?,
        delta: Int,
    ): List<ProductDto> {
        val updated = StockMath.applyDelta(cache.load(storeId), productId, sku, size, color, delta)
        cache.save(storeId, updated)
        return updated
    }

    /** Доставка корректировки (outbox): применяет на сервере (идемпотентно по ключу),
     *  пишет авторитетный остаток в кэш. */
    suspend fun adjustStock(
        storeId: String,
        productId: String,
        body: StockAdjustRequest,
    ): ApiResult<StockResultDto> {
        val r = safeApiCall(json) { api.adjustStock(storeId, productId, body) }
        if (r is ApiResult.Success) {
            val updated = cache.load(storeId).map { p ->
                if (p.id == productId) {
                    p.copy(totalStock = r.data.totalStock, variants = r.data.variants)
                } else {
                    p
                }
            }
            cache.save(storeId, updated)
        }
        return r
    }
}
