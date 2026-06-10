package com.wasat.shop.core.network

import com.wasat.shop.core.network.dto.StoreInitRequest
import com.wasat.shop.core.network.dto.StoreInitResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** REST-клиент сервера (docs/api-contract.md). Эндпоинты добавляются по фазам. */
interface WasatApi {
    @POST("api/stores/init")
    suspend fun initStore(@Body body: StoreInitRequest): Response<StoreInitResponse>
}
