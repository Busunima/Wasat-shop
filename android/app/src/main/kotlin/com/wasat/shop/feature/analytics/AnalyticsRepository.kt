package com.wasat.shop.feature.analytics

import com.wasat.shop.core.network.ApiResult
import com.wasat.shop.core.network.WasatApi
import com.wasat.shop.core.network.dto.AnalyticsEventRequest
import com.wasat.shop.core.network.dto.AnalyticsReportDto
import com.wasat.shop.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Продуктовые события (§16) и дашборд (FR-A05). События отправляются
 * fire-and-forget (сбой не мешает UX); дашборд читается владельцем.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val api: WasatApi,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun track(storeId: String, type: String, productId: String? = null, value: Long? = null) {
        scope.launch {
            runCatching {
                api.recordEvent(
                    storeId,
                    AnalyticsEventRequest(type = type, productId = productId, value = value),
                )
            }
        }
    }

    suspend fun report(storeId: String, params: Map<String, String>): ApiResult<AnalyticsReportDto> =
        safeApiCall(json) { api.analytics(storeId, params) }
}
