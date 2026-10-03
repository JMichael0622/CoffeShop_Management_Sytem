package com.project2.coffeeshoppos.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.MetricCard
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.theme.CoffeeShopPOSTheme
import com.project2.coffeeshoppos.ui.theme.LocalCoffeeExtraColors
import com.project2.coffeeshoppos.util.formatPeso
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NOT_AVAILABLE = "—"

private fun Long?.asPeso(): String = this?.let(::formatPeso) ?: "₱$NOT_AVAILABLE"
private fun Int?.asCount(): String = this?.toString() ?: NOT_AVAILABLE

@Composable
fun DashboardScreen(
    onNewOrder: () -> Unit,
    onAddStock: () -> Unit,
    onRecordExpense: () -> Unit,
    onOpenManage: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        onNewOrder = onNewOrder,
        onAddStock = onAddStock,
        onRecordExpense = onRecordExpense,
        onOpenManage = onOpenManage,
        onOpenOrder = onOpenOrder
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onNewOrder: () -> Unit,
    onAddStock: () -> Unit,
    onRecordExpense: () -> Unit,
    onOpenManage: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DashboardHeader(onOpenManage = onOpenManage) }

        item {
            QuickActions(
                onNewOrder = onNewOrder,
                onAddStock = onAddStock,
                onRecordExpense = onRecordExpense
            )
        }

        item { ProfitHeroCard(profit = state.estimatedProfitCentavos.asPeso()) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "Gross sales",
                    value = state.grossSalesCentavos.asPeso(),
                    icon = R.drawable.ic_trending_up,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Completed orders",
                    value = state.completedOrders.asCount(),
                    icon = R.drawable.ic_receipt,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "Discounts & refunds",
                    value = state.discountsAndRefundsCentavos.asPeso(),
                    icon = R.drawable.ic_discount,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Cost of goods",
                    value = state.cogsCentavos.asPeso(),
                    icon = R.drawable.ic_cafe,
                    supportingText = "COGS",
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            MetricCard(
                label = "Operating expenses",
                value = state.operatingExpensesCentavos.asPeso(),
                icon = R.drawable.ic_nav_expenses,
                supportingText = "Incurred today. Stock purchases are tracked separately.",
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { SectionHeader("Stock alerts") }
        item { StockAlertsCard(state) }

        item { SectionHeader("Best sellers today") }
        item { BestSellersCard(state.bestSellers) }

        item { SectionHeader("Recent orders") }
        item { RecentOrdersCard(state.recentOrders, onOpenOrder) }
    }
}

@Composable
private fun DashboardHeader(onOpenManage: () -> Unit) {
    val now = remember { LocalDateTime.now() }
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))
    }
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Here's how the shop is doing today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpenManage) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Manage shop",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActions(
    onNewOrder: () -> Unit,
    onAddStock: () -> Unit,
    onRecordExpense: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickActionTile(
            label = "New order",
            icon = R.drawable.ic_add,
            onClick = onNewOrder,
            primary = true,
            modifier = Modifier.weight(1f)
        )
        QuickActionTile(
            label = "Add stock",
            icon = R.drawable.ic_nav_inventory,
            onClick = onAddStock,
            modifier = Modifier.weight(1f)
        )
        QuickActionTile(
            label = "Expense",
            icon = R.drawable.ic_nav_expenses,
            onClick = onRecordExpense,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 76.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (primary) colors.tertiary else colors.surfaceContainerLowest,
        contentColor = if (primary) colors.onTertiary else colors.onSurface,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            Icon(painter = painterResource(icon), contentDescription = null)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfitHeroCard(profit: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.primary,
        contentColor = colors.onPrimary
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_trending_up),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text("Estimated profit today", style = MaterialTheme.typography.labelLarge)
            }
            Text(text = profit, style = MaterialTheme.typography.displaySmall)
            Text(
                text = "Net sales − COGS − stock losses − operating expenses",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimary.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun StockAlertsCard(state: DashboardUiState) {
    val warning = LocalCoffeeExtraColors.current.warning
    CoffeeCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StockCountPill(
                    label = "Low stock",
                    count = state.lowStockCount.asCount(),
                    color = warning,
                    modifier = Modifier.weight(1f)
                )
                StockCountPill(
                    label = "Out of stock",
                    count = state.outOfStockCount.asCount(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }
            if (state.stockAlerts.isEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                EmptyState(
                    icon = R.drawable.ic_nav_inventory,
                    title = "No stock alerts",
                    message = "Ingredients at or below their reorder level are listed here."
                )
            } else {
                state.stockAlerts.forEach { alert ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListRow(
                        icon = R.drawable.ic_warning,
                        iconTint = if (alert.isOutOfStock) MaterialTheme.colorScheme.error else warning,
                        title = alert.ingredientName,
                        subtitle = if (alert.isOutOfStock) "Out of stock" else "Low stock",
                        trailing = alert.quantityLabel
                    )
                }
            }
        }
    }
}

@Composable
private fun StockCountPill(
    label: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BestSellersCard(bestSellers: List<BestSeller>) {
    CoffeeCard(modifier = Modifier.fillMaxWidth()) {
        if (bestSellers.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_cafe,
                title = "No sales yet today",
                message = "Your top products will show up after orders are completed."
            )
        } else {
            Column {
                bestSellers.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListRow(
                        icon = R.drawable.ic_cafe,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = item.productName,
                        subtitle = "${item.quantitySold} sold",
                        trailing = formatPeso(item.salesCentavos)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentOrdersCard(orders: List<RecentOrder>, onOpenOrder: (Long) -> Unit) {
    CoffeeCard(modifier = Modifier.fillMaxWidth()) {
        if (orders.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_receipt,
                title = "No orders yet",
                message = "Tap \"New order\" to ring up your first sale."
            )
        } else {
            Column {
                orders.forEachIndexed { index, order ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListRow(
                        icon = R.drawable.ic_receipt,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = order.orderNumber,
                        subtitle = "${order.timeLabel} · ${order.statusLabel}",
                        trailing = formatPeso(order.totalCentavos),
                        onClick = { onOpenOrder(order.orderId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ListRow(
    @DrawableRes icon: Int,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailing: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(text = trailing, style = MaterialTheme.typography.titleSmall)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF5EC, heightDp = 1400)
@Composable
private fun DashboardScreenPreview() {
    CoffeeShopPOSTheme(darkTheme = false) {
        DashboardContent(
            state = DashboardUiState(),
            onNewOrder = {},
            onAddStock = {},
            onRecordExpense = {},
            onOpenManage = {},
            onOpenOrder = {}
        )
    }
}
