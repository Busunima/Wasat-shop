package com.wasat.shop.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wasat.shop.R
import com.wasat.shop.core.network.dto.AnalyticsReportDto
import com.wasat.shop.core.util.PriceFormatter

/** Дашборд аналитики (FR-A05): KPI, воронка, конверсии, топ-товары. */
@Composable
fun AnalyticsScreen(
    currency: String,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    when (val s = state) {
        AnalyticsUiState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        is AnalyticsUiState.Error -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = viewModel::load) { Text(stringResource(R.string.catalog_retry)) }
            }
        }

        is AnalyticsUiState.Loaded -> AnalyticsContent(s.report, currency)
    }
}

@Composable
private fun AnalyticsContent(report: AnalyticsReportDto, currency: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.analytics_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "${report.from} — ${report.to}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )

        // KPI
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Kpi(
                label = stringResource(R.string.analytics_revenue),
                value = PriceFormatter.format(report.revenue, currency),
                modifier = Modifier.weight(1f),
            )
            Kpi(
                label = stringResource(R.string.analytics_orders),
                value = report.orders.toString(),
                modifier = Modifier.weight(1f),
            )
            Kpi(
                label = stringResource(R.string.analytics_avg_check),
                value = PriceFormatter.format(report.avgCheck, currency),
                modifier = Modifier.weight(1f),
            )
        }

        // FR-A05: новые vs вернувшиеся покупатели за период
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Kpi(
                label = stringResource(R.string.analytics_new_customers),
                value = report.customers.new.toString(),
                modifier = Modifier.weight(1f),
            )
            Kpi(
                label = stringResource(R.string.analytics_returning_customers),
                value = report.customers.returning.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider()

        // Воронка просмотр → корзина → оформление → заказ
        Text(
            text = stringResource(R.string.analytics_funnel),
            style = MaterialTheme.typography.titleMedium,
        )
        val maxFunnel = report.funnel.views.coerceAtLeast(1)
        FunnelBar(stringResource(R.string.analytics_views), report.funnel.views, maxFunnel)
        FunnelBar(stringResource(R.string.analytics_carts), report.funnel.addToCarts, maxFunnel)
        FunnelBar(stringResource(R.string.analytics_checkouts), report.funnel.checkouts, maxFunnel)
        FunnelBar(stringResource(R.string.analytics_purchases), report.funnel.purchases, maxFunnel)

        Text(
            text = stringResource(
                R.string.analytics_conversion,
                report.conversion.viewToCart,
                report.conversion.cartToOrder,
                report.conversion.viewToOrder,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )

        if (report.topProducts.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.analytics_top),
                style = MaterialTheme.typography.titleMedium,
            )
            report.topProducts.forEach { tp ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(tp.productId.take(12), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.analytics_views_count, tp.views),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FunnelBar(label: String, count: Int, max: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$count", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { count.toFloat() / max.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        )
    }
}
