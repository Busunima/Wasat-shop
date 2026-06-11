package com.wasat.shop.core.network

import com.wasat.shop.core.network.dto.AnalyticsEventRequest
import com.wasat.shop.core.network.dto.AnalyticsReportDto
import com.wasat.shop.core.network.dto.ImportReportDto
import com.wasat.shop.core.network.dto.InventoryLogResponse
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.ProductListResponse
import com.wasat.shop.core.network.dto.ProductUpsertRequest
import com.wasat.shop.core.network.dto.PromoCreateRequest
import com.wasat.shop.core.network.dto.PromoDto
import com.wasat.shop.core.network.dto.PromoListResponse
import com.wasat.shop.core.network.dto.PromoPreviewRequest
import com.wasat.shop.core.network.dto.PromoPreviewResponse
import com.wasat.shop.core.network.dto.StoreInfoDto
import com.wasat.shop.core.network.dto.StoreInitRequest
import com.wasat.shop.core.network.dto.StockAdjustRequest
import com.wasat.shop.core.network.dto.StockResultDto
import com.wasat.shop.core.network.dto.StoreUpdateRequest
import okhttp3.RequestBody
import com.wasat.shop.core.network.dto.StoreInitResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap

/** REST-клиент сервера (docs/api-contract.md). Эндпоинты добавляются по фазам. */
interface WasatApi {
    @POST("api/stores/init")
    suspend fun initStore(@Body body: StoreInitRequest): Response<StoreInitResponse>

    @GET("api/stores/{storeId}")
    suspend fun getStore(@Path("storeId") storeId: String): Response<StoreInfoDto>

    @GET("api/stores/by-slug/{slug}")
    suspend fun getStoreBySlug(@Path("slug") slug: String): Response<StoreInfoDto>

    @PATCH("api/stores/{storeId}")
    suspend fun updateStore(
        @Path("storeId") storeId: String,
        @Body body: StoreUpdateRequest,
    ): Response<StoreInfoDto>

    @GET("api/stores/{storeId}/products")
    suspend fun listProducts(
        @Path("storeId") storeId: String,
        @QueryMap params: Map<String, String>,
    ): Response<ProductListResponse>

    @GET("api/stores/{storeId}/products/{productId}")
    suspend fun getProduct(
        @Path("storeId") storeId: String,
        @Path("productId") productId: String,
    ): Response<ProductDto>

    @POST("api/stores/{storeId}/products")
    suspend fun createProduct(
        @Path("storeId") storeId: String,
        @Body body: ProductUpsertRequest,
    ): Response<ProductDto>

    @PATCH("api/stores/{storeId}/products/{productId}")
    suspend fun updateProduct(
        @Path("storeId") storeId: String,
        @Path("productId") productId: String,
        @Body body: ProductUpsertRequest,
    ): Response<ProductDto>

    @DELETE("api/stores/{storeId}/products/{productId}")
    suspend fun deleteProduct(
        @Path("storeId") storeId: String,
        @Path("productId") productId: String,
    ): Response<Unit>

    // ── Инвентарь (FR-A03) ───────────────────────────────────────────────────

    @POST("api/stores/{storeId}/inventory/products/{productId}/stock")
    suspend fun adjustStock(
        @Path("storeId") storeId: String,
        @Path("productId") productId: String,
        @Body body: StockAdjustRequest,
    ): Response<StockResultDto>

    /** CSV «sku,stock» (text/csv) — абсолютные значения остатков. */
    @POST("api/stores/{storeId}/inventory/import")
    suspend fun importStockCsv(
        @Path("storeId") storeId: String,
        @Body csv: RequestBody,
    ): Response<ImportReportDto>

    @GET("api/stores/{storeId}/inventory/log")
    suspend fun inventoryLog(
        @Path("storeId") storeId: String,
        @QueryMap params: Map<String, String>,
    ): Response<InventoryLogResponse>

    // ── Продуктовые события и аналитика (§16, FR-A05) ────────────────────────

    @POST("api/stores/{storeId}/events")
    suspend fun recordEvent(
        @Path("storeId") storeId: String,
        @Body body: AnalyticsEventRequest,
    ): Response<Unit>

    @GET("api/stores/{storeId}/analytics")
    suspend fun analytics(
        @Path("storeId") storeId: String,
        @QueryMap params: Map<String, String>,
    ): Response<AnalyticsReportDto>

    // ── Промокоды (FR-A06) ───────────────────────────────────────────────────

    @GET("api/stores/{storeId}/promocodes")
    suspend fun listPromocodes(
        @Path("storeId") storeId: String,
    ): Response<PromoListResponse>

    @POST("api/stores/{storeId}/promocodes")
    suspend fun createPromocode(
        @Path("storeId") storeId: String,
        @Body body: PromoCreateRequest,
    ): Response<PromoDto>

    @DELETE("api/stores/{storeId}/promocodes/{code}")
    suspend fun deletePromocode(
        @Path("storeId") storeId: String,
        @Path("code") code: String,
    ): Response<Unit>

    /** Публичный предпросмотр скидки для корзины (FR-B04). */
    @POST("api/stores/{storeId}/promocodes/preview")
    suspend fun previewPromocode(
        @Path("storeId") storeId: String,
        @Body body: PromoPreviewRequest,
    ): Response<PromoPreviewResponse>
}
