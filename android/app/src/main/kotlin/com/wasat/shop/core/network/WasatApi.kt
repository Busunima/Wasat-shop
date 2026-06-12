package com.wasat.shop.core.network

import com.wasat.shop.core.network.dto.AiDescribeRequest
import com.wasat.shop.core.network.dto.AiDescribeResponse
import com.wasat.shop.core.network.dto.AnalyticsEventRequest
import com.wasat.shop.core.network.dto.AnalyticsReportDto
import com.wasat.shop.core.network.dto.CheckoutRequest
import com.wasat.shop.core.network.dto.OrderDto
import com.wasat.shop.core.network.dto.OrderListResponse
import com.wasat.shop.core.network.dto.OrderStatusUpdateRequest
import com.wasat.shop.core.network.dto.ImportReportDto
import com.wasat.shop.core.network.dto.InventoryLogResponse
import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.PlanUsageDto
import com.wasat.shop.core.network.dto.ProductListResponse
import com.wasat.shop.core.network.dto.ProductUpsertRequest
import com.wasat.shop.core.network.dto.PromoCreateRequest
import com.wasat.shop.core.network.dto.PromoDto
import com.wasat.shop.core.network.dto.PromoListResponse
import com.wasat.shop.core.network.dto.PromoPreviewRequest
import com.wasat.shop.core.network.dto.PromoPreviewResponse
import com.wasat.shop.core.network.dto.PushTokenRequest
import com.wasat.shop.core.network.dto.ReturnCreateRequest
import com.wasat.shop.core.network.dto.ReturnDto
import com.wasat.shop.core.network.dto.ReturnListResponse
import com.wasat.shop.core.network.dto.ReturnResolveRequest
import com.wasat.shop.core.network.dto.ReviewCreateRequest
import com.wasat.shop.core.network.dto.ReviewDto
import com.wasat.shop.core.network.dto.ReviewListResponse
import com.wasat.shop.core.network.dto.StaffInviteRequest
import com.wasat.shop.core.network.dto.StaffListResponse
import com.wasat.shop.core.network.dto.StaffMemberDto
import com.wasat.shop.core.network.dto.StaffRoleUpdateRequest
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

    /** Тариф, лимиты и использование (FR-S03), только владелец. */
    @GET("api/stores/{storeId}/plan")
    suspend fun storePlan(@Path("storeId") storeId: String): Response<PlanUsageDto>

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

    // ── Отзывы (FR-B08) ──────────────────────────────────────────────────────

    @GET("api/stores/{storeId}/products/{productId}/reviews")
    suspend fun listReviews(
        @Path("storeId") storeId: String,
        @Path("productId") productId: String,
        @QueryMap params: Map<String, String>,
    ): Response<ReviewListResponse>

    @POST("api/stores/{storeId}/products/{productId}/reviews")
    suspend fun createReview(
        @Path("storeId") storeId: String,
        @Path("productId") productId: String,
        @Body body: ReviewCreateRequest,
    ): Response<ReviewDto>

    // ── Рекомендации (FR-B12) ────────────────────────────────────────────────

    @GET("api/stores/{storeId}/recommendations/popular")
    suspend fun popularProducts(
        @Path("storeId") storeId: String,
        @QueryMap params: Map<String, String>,
    ): Response<ProductListResponse>

    @GET("api/stores/{storeId}/recommendations/related/{productId}")
    suspend fun relatedProducts(
        @Path("storeId") storeId: String,
        @Path("productId") productId: String,
        @QueryMap params: Map<String, String>,
    ): Response<ProductListResponse>

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

    // ── Заказы (FR-B05/A04/B06) ──────────────────────────────────────────────

    /** Транзакционный чекаут (§10.1); повтор idempotencyKey → 200 + тот же заказ. */
    @POST("api/checkout")
    suspend fun checkout(@Body body: CheckoutRequest): Response<OrderDto>

    /** Заказы магазина — владелец/сотрудник (FR-A04). */
    @GET("api/stores/{storeId}/orders")
    suspend fun storeOrders(
        @Path("storeId") storeId: String,
        @QueryMap params: Map<String, String>,
    ): Response<OrderListResponse>

    /** Заказы покупателя (FR-B06). */
    @GET("api/stores/{storeId}/orders/my")
    suspend fun myOrders(
        @Path("storeId") storeId: String,
        @QueryMap params: Map<String, String>,
    ): Response<OrderListResponse>

    @GET("api/stores/{storeId}/orders/{orderId}")
    suspend fun order(
        @Path("storeId") storeId: String,
        @Path("orderId") orderId: String,
    ): Response<OrderDto>

    @POST("api/stores/{storeId}/orders/{orderId}/status")
    suspend fun updateOrderStatus(
        @Path("storeId") storeId: String,
        @Path("orderId") orderId: String,
        @Body body: OrderStatusUpdateRequest,
    ): Response<OrderDto>

    @POST("api/stores/{storeId}/orders/{orderId}/cancel")
    suspend fun cancelOrder(
        @Path("storeId") storeId: String,
        @Path("orderId") orderId: String,
    ): Response<OrderDto>

    // ── Возвраты (FR-B09/A11) ────────────────────────────────────────────────

    @POST("api/stores/{storeId}/returns")
    suspend fun createReturn(
        @Path("storeId") storeId: String,
        @Body body: ReturnCreateRequest,
    ): Response<ReturnDto>

    @GET("api/stores/{storeId}/returns/my")
    suspend fun myReturns(@Path("storeId") storeId: String): Response<ReturnListResponse>

    @GET("api/stores/{storeId}/returns")
    suspend fun storeReturns(
        @Path("storeId") storeId: String,
        @QueryMap params: Map<String, String>,
    ): Response<ReturnListResponse>

    @POST("api/stores/{storeId}/returns/{returnId}/resolve")
    suspend fun resolveReturn(
        @Path("storeId") storeId: String,
        @Path("returnId") returnId: String,
        @Body body: ReturnResolveRequest,
    ): Response<ReturnDto>

    @POST("api/stores/{storeId}/returns/{returnId}/receive")
    suspend fun receiveReturn(
        @Path("storeId") storeId: String,
        @Path("returnId") returnId: String,
    ): Response<ReturnDto>

    @POST("api/stores/{storeId}/returns/{returnId}/refund")
    suspend fun refundReturn(
        @Path("storeId") storeId: String,
        @Path("returnId") returnId: String,
    ): Response<ReturnDto>

    // ── AI-ассист контента (FR-A12) ──────────────────────────────────────────

    /** Генерация описания товара; 501 NOT_IMPLEMENTED без ANTHROPIC_API_KEY. */
    @POST("api/stores/{storeId}/ai/describe")
    suspend fun aiDescribe(
        @Path("storeId") storeId: String,
        @Body body: AiDescribeRequest,
    ): Response<AiDescribeResponse>

    // ── Push-уведомления (FR-B10) ────────────────────────────────────────────

    /** Регистрация FCM-токена устройства (любой авторизованный покупатель). */
    @POST("api/stores/{storeId}/push-tokens")
    suspend fun registerPushToken(
        @Path("storeId") storeId: String,
        @Body body: PushTokenRequest,
    ): Response<Unit>

    // ── Сотрудники (FR-A09) ──────────────────────────────────────────────────

    @GET("api/stores/{storeId}/staff")
    suspend fun listStaff(
        @Path("storeId") storeId: String,
    ): Response<StaffListResponse>

    @POST("api/stores/{storeId}/staff")
    suspend fun addStaff(
        @Path("storeId") storeId: String,
        @Body body: StaffInviteRequest,
    ): Response<StaffMemberDto>

    @PATCH("api/stores/{storeId}/staff/{uid}")
    suspend fun updateStaffRole(
        @Path("storeId") storeId: String,
        @Path("uid") uid: String,
        @Body body: StaffRoleUpdateRequest,
    ): Response<StaffMemberDto>

    @DELETE("api/stores/{storeId}/staff/{uid}")
    suspend fun removeStaff(
        @Path("storeId") storeId: String,
        @Path("uid") uid: String,
    ): Response<Unit>
}
