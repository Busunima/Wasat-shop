package com.wasat.shop.core.network

import com.wasat.shop.core.network.dto.ProductDto
import com.wasat.shop.core.network.dto.ProductListResponse
import com.wasat.shop.core.network.dto.ProductUpsertRequest
import com.wasat.shop.core.network.dto.StoreInfoDto
import com.wasat.shop.core.network.dto.StoreInitRequest
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
}
