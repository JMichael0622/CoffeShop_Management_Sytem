package com.project2.coffeeshoppos.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.dao.OrderListRow
import com.project2.coffeeshoppos.data.repository.shortOrderNumber
import com.project2.coffeeshoppos.domain.OrderStatus
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.util.formatPeso
import java.time.ZoneId

@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    viewModel: OrdersViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)

    SubScreenScaffold(title = "Orders", onBack = onBack, snackbarHostState = snackbarHostState) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@SubScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.tab == OrdersTab.ACTIVE,
                        onClick = { viewModel.onTab(OrdersTab.ACTIVE) },
                        label = { Text("In progress (${state.active.size})") }
                    )
                    FilterChip(
                        selected = state.tab == OrdersTab.HISTORY,
                        onClick = { viewModel.onTab(OrdersTab.HISTORY) },
                        label = { Text("History") }
                    )
                }
            }
            if (state.tab == OrdersTab.ACTIVE) {
                if (state.active.isEmpty()) {
                    item {
                        CoffeeCard(Modifier.fillMaxWidth()) {
                            EmptyState(
                                icon = R.drawable.ic_receipt,
                                title = "No orders waiting",
                                message = "Paid orders show up here until they are handed over."
                            )
                        }
                    }
                }
                items(state.active, key = { it.order.id }) { row ->
                    OrderRow(
                        row = row,
                        onClick = { onOpenOrder(row.order.id) },
                        actions = {
                            if (row.order.status == OrderStatus.PENDING) {
                                OutlinedButton(onClick = { viewModel.start(row.order.id) }) { Text("Start") }
                            }
                            Button(onClick = { viewModel.complete(row.order.id) }) { Text("Complete") }
                        }
                    )
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.onDate(state.date.minusDays(1)) }) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Previous day")
                        }
                        Text(
                            if (state.date == state.today) "Today" else state.date.format(ORDER_DATE),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.onDate(state.date.plusDays(1)) }, enabled = state.date < state.today) {
                            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next day")
                        }
                    }
                }
                item {
                    Text(
                        "${state.dayCompletedCount} completed · ${formatPeso(state.daySalesCentavos)} in sales",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.dayOrders.isEmpty()) {
                    item {
                        CoffeeCard(Modifier.fillMaxWidth()) {
                            EmptyState(icon = R.drawable.ic_receipt, title = "No orders", message = "No orders were taken on this day.")
                        }
                    }
                }
                items(state.dayOrders, key = { it.order.id }) { row ->
                    OrderRow(row = row, onClick = { onOpenOrder(row.order.id) })
                }
            }
        }
    }
}

@Composable
private fun OrderRow(row: OrderListRow, onClick: () -> Unit, actions: (@Composable () -> Unit)? = null) {
    val zone = remember { ZoneId.systemDefault() }
    val order = row.order
    CoffeeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onClick).heightIn(min = 64.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(shortOrderNumber(order.orderNumber), style = MaterialTheme.typography.titleMedium)
                OrderStatusChip(order.status)
                Text(
                    formatPeso(order.totalCentavos),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                listOfNotNull(
                    order.createdAt.atZone(zone).format(ORDER_TIME),
                    "${row.itemCount} " + if (row.itemCount == 1) "item" else "items",
                    row.paymentMethod?.label,
                    order.note.ifBlank { null }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actions != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) { actions() }
            }
        }
    }
}
