package com.wasat.shop.feature.onboarding

import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.StoreInitRequest
import com.wasat.shop.core.network.dto.StoreInitResponse
import com.wasat.shop.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Вызовы серверного API магазина с маппингом конверта ошибок в [ApiResult]. */
@Singleton
class StoreRepository @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
) {
    suspend fun initStore(request: StoreInitRequest): ApiResult<StoreInitResponse> =
        safeApiCall(json) { api.initStore(request) }
}
