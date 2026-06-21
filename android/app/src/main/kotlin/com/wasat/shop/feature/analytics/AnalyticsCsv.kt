package com.wasat.shop.feature.analytics

import com.wasat.shop.core.network.dto.AnalyticsReportDto

/**
 * Сериализация дашборда (FR-A05) в CSV для экспорта: сводка за период + разбивка
 * по дням. Суммы — в минорных единицах валюты магазина. Pure JVM (под тестом).
 */
object AnalyticsCsv {
    fun build(report: AnalyticsReportDto): String = buildString {
        appendLine("period,${report.from},${report.to}")
        appendLine("revenue,${report.revenue}")
        appendLine("orders,${report.orders}")
        appendLine("avg_check,${report.avgCheck}")
        appendLine("new_customers,${report.customers.new}")
        appendLine("returning_customers,${report.customers.returning}")
        appendLine()
        appendLine("date,views,orders,revenue")
        report.daily.forEach { d ->
            appendLine("${d.date},${d.views},${d.orders},${d.revenue}")
        }
    }
}
