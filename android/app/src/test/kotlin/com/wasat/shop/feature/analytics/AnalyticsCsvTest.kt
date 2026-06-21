package com.wasat.shop.feature.analytics

import com.wasat.shop.core.network.dto.AnalyticsReportDto
import com.wasat.shop.core.network.dto.CustomersDto
import com.wasat.shop.core.network.dto.DailyPointDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsCsvTest {

    private val report = AnalyticsReportDto(
        from = "2026-06-01",
        to = "2026-06-02",
        revenue = 12990,
        orders = 3,
        avgCheck = 4330,
        customers = CustomersDto(new = 2, returning = 1),
        daily = listOf(
            DailyPointDto(date = "2026-06-01", views = 10, orders = 1, revenue = 4990),
            DailyPointDto(date = "2026-06-02", views = 20, orders = 2, revenue = 8000),
        ),
    )

    @Test
    fun `build - сводка и разбивка по дням в CSV`() {
        val csv = AnalyticsCsv.build(report)
        val lines = csv.trim().lines()

        assertEquals("period,2026-06-01,2026-06-02", lines[0])
        assertTrue("revenue,12990" in lines)
        assertTrue("new_customers,2" in lines)
        assertTrue("returning_customers,1" in lines)
        // Заголовок дневной таблицы и строки по датам
        assertTrue("date,views,orders,revenue" in lines)
        assertTrue("2026-06-01,10,1,4990" in lines)
        assertTrue("2026-06-02,20,2,8000" in lines)
    }

    @Test
    fun `build - пустой daily даёт только сводку без строк дат`() {
        val csv = AnalyticsCsv.build(report.copy(daily = emptyList()))
        assertTrue("date,views,orders,revenue" in csv)
        assertTrue(csv.trim().lines().none { it.matches(Regex("""\d{4}-\d{2}-\d{2},.*""")) })
    }
}
