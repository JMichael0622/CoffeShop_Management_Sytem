package com.project2.coffeeshoppos.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.repository.SalesReport
import com.project2.coffeeshoppos.domain.DateRange
import com.project2.coffeeshoppos.domain.ReportPeriod
import com.project2.coffeeshoppos.domain.costPercent
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.DatePickDialog
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.MetricCard
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.util.formatPeso
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
private val DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

private enum class EditingDate { START, END }

/** The Reports tab: profit and sales for a chosen period. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<EditingDate?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ReportPeriod.entries) { period ->
                        FilterChip(selected = state.period == period, onClick = { viewModel.onPeriod(period) }, label = { Text(period.label) })
                    }
                }
            }
            item {
                if (state.period == ReportPeriod.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = EditingDate.START }, modifier = Modifier.weight(1f)) {
                            Text("From ${state.range.start.format(DAY_YEAR)}")
                        }
                        OutlinedButton(onClick = { editing = EditingDate.END }, modifier = Modifier.weight(1f)) {
                            Text("To ${state.range.endInclusive.format(DAY_YEAR)}")
                        }
                    }
                } else {
                    Text(rangeLabel(state.range), style = MaterialTheme.typography.titleMedium)
                }
            }
            val report = state.report
            if (report == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else {
                reportSections(report)
            }
        }
    }

    when (editing) {
        EditingDate.START -> DatePickDialog(
            initial = state.range.start,
            latest = state.today,
            onPick = viewModel::onCustomStart,
            onDismiss = { editing = null }
        )
        EditingDate.END -> DatePickDialog(
            initial = state.range.endInclusive,
            latest = state.today,
            onPick = viewModel::onCustomEnd,
            onDismiss = { editing = null }
        )
        null -> Unit
    }
}

private fun rangeLabel(range: DateRange): String =
    if (range.isSingleDay) range.start.format(DAY_YEAR)
    else "${range.start.format(DAY_YEAR)} – ${range.endInclusive.format(DAY_YEAR)}"

private fun LazyListScope.reportSections(report: SalesReport) {
    val summary = report.summary
    item { ProfitStatement(report) }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                label = "Orders completed",
                value = summary.orderCount.toString(),
                icon = R.drawable.ic_receipt,
                supportingText = listOfNotNull(
                    report.cancelledCount.takeIf { it > 0 }?.let { "$it cancelled" },
                    report.refundedCount.takeIf { it > 0 }?.let { "$it refunded" }
                ).joinToString(" · ").ifEmpty { null },
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Average order",
                value = summary.averageOrderCentavos?.let(::formatPeso) ?: "—",
                icon = R.drawable.ic_trending_up,
                supportingText = "After discounts",
                modifier = Modifier.weight(1f)
            )
        }
    }

    item { SectionHeader("Top products") }
    item {
        CoffeeCard(Modifier.fillMaxWidth()) {
            if (report.productSales.isEmpty()) {
                EmptyState(icon = R.drawable.ic_cafe, title = "No sales", message = "Completed orders in this period will show here.")
            } else {
                Column(Modifier.padding(vertical = 4.dp)) {
                    report.productSales.take(TOP_PRODUCTS).forEachIndexed { index, product ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(product.productName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${product.quantity} sold" + (costPercent(product.cogsCentavos, product.salesCentavos)?.let { " · ingredients $it% of price" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(formatPeso(product.salesCentavos), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }

    if (report.salesByMethod.isNotEmpty()) {
        item { SectionHeader("Paid by") }
        item {
            CoffeeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.salesByMethod.forEach { LineItem(it.method.label, formatPeso(it.amountCentavos)) }
                    if (summary.refundsCentavos > 0) {
                        Text(
                            "Before ${formatPeso(summary.refundsCentavos)} in refunds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    item { SectionHeader("Operating expenses") }
    item {
        CoffeeCard(Modifier.fillMaxWidth()) {
            if (report.expensesByCategory.isEmpty()) {
                EmptyState(icon = R.drawable.ic_nav_expenses, title = "No expenses", message = "None recorded for these dates.")
            } else {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.expensesByCategory.forEach { (category, amount) -> LineItem(category.label, formatPeso(amount)) }
                }
            }
        }
    }

    item { SectionHeader("Stock") }
    item {
        CoffeeCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LineItem("Stock purchased", formatPeso(report.purchasesCentavos))
                LineItem("Wastage", formatPeso(report.wastageCentavos))
                Text(
                    "Purchases are cash spent on stock, not a cost in profit: ingredients count as cost of goods when " +
                        "they are sold, or as losses when wasted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (!report.range.isSingleDay) {
        item { SectionHeader("Day by day") }
        item {
            CoffeeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        TableCell("Day", Modifier.weight(1.4f), header = true)
                        TableCell("Orders", Modifier.weight(0.8f), header = true, end = true)
                        TableCell("Net sales", Modifier.weight(1.2f), header = true, end = true)
                        TableCell("Expenses", Modifier.weight(1.2f), header = true, end = true)
                    }
                    report.days.asReversed().forEach { day ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row {
                            TableCell(day.date.format(DAY), Modifier.weight(1.4f))
                            TableCell(day.orderCount.toString(), Modifier.weight(0.8f), end = true)
                            TableCell(formatPeso(day.netSalesCentavos), Modifier.weight(1.2f), end = true)
                            TableCell(formatPeso(day.expensesCentavos), Modifier.weight(1.2f), end = true)
                        }
                    }
                }
            }
        }
    }

    item {
        Text(
            "Sales count when an order is completed. Refunds count on the day they are given. " +
                "Expenses count on their expense date, paid or not.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val TOP_PRODUCTS = 10

@Composable
private fun ProfitStatement(report: SalesReport) {
    val s = report.summary
    val margin = if (s.netSalesCentavos > 0) costPercent(s.grossProfitCentavos, s.netSalesCentavos) else null
    CoffeeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Net profit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatPeso(s.netProfitCentavos),
                style = MaterialTheme.typography.headlineMedium,
                color = if (s.netProfitCentavos < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
            LineItem("Gross sales", formatPeso(s.grossSalesCentavos))
            LineItem("Discounts", "−" + formatPeso(s.discountsCentavos))
            LineItem("Refunds", "−" + formatPeso(s.refundsCentavos))
            LineItem("Net sales", formatPeso(s.netSalesCentavos), bold = true)
            LineItem("Cost of goods", "−" + formatPeso(s.cogsCentavos))
            LineItem("Gross profit" + (margin?.let { " ($it%)" } ?: ""), formatPeso(s.grossProfitCentavos), bold = true)
            LineItem("Stock losses", signedCost(s.stockLossCentavos))
            LineItem("Operating expenses", "−" + formatPeso(s.operatingExpensesCentavos))
            LineItem("Net profit", formatPeso(s.netProfitCentavos), bold = true)
        }
    }
}

/** A loss shows as "−₱x"; a gain (more stock counted than recorded) as "+₱x". */
private fun signedCost(lossCentavos: Long): String =
    if (lossCentavos >= 0) "−" + formatPeso(lossCentavos) else "+" + formatPeso(-lossCentavos)

@Composable
private fun LineItem(label: String, value: String, bold: Boolean = false) {
    val weight = if (bold) FontWeight.SemiBold else FontWeight.Normal
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
    }
}

@Composable
private fun TableCell(text: String, modifier: Modifier, header: Boolean = false, end: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
        maxLines = 1
    )
}
