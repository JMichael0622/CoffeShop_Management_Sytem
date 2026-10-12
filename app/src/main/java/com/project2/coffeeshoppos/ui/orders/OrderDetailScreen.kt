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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.dao.CustomerSummary
import com.project2.coffeeshoppos.data.local.entity.LoyaltyEntryEntity
import com.project2.coffeeshoppos.data.repository.OrderDetail
import com.project2.coffeeshoppos.data.repository.OrderLineDetail
import com.project2.coffeeshoppos.data.repository.shortOrderNumber
import com.project2.coffeeshoppos.domain.OrderStatus
import com.project2.coffeeshoppos.domain.isActive
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.FormDialog
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.util.formatPeso
import java.time.Instant
import java.time.ZoneId

@Composable
fun OrderDetailScreen(
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    viewModel: OrderDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var cancelling by rememberSaveable { mutableStateOf(false) }
    var refunding by rememberSaveable { mutableStateOf(false) }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)
    val detail = state.detail

    SubScreenScaffold(
        title = detail?.let { "Order ${shortOrderNumber(it.order.orderNumber)}" } ?: "Order",
        onBack = onBack,
        snackbarHostState = snackbarHostState
    ) { innerPadding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            detail == null -> Box(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    EmptyState(icon = R.drawable.ic_receipt, title = "Order not found", message = "Go back to the order list.")
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { HeaderCard(detail) }
                item { ItemsCard(detail) }
                state.customer?.let { customer ->
                    item { LoyaltyCard(customer, state.loyaltyEntries, onOpenCustomer) }
                }
                item { PaymentCard(detail) }
                val order = detail.order
                if (order.cogsCentavos != null) {
                    item {
                        CoffeeCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DetailRow("Ingredient cost", formatPeso(order.cogsCentavos))
                                DetailRow("Gross profit", formatPeso(order.totalCentavos - order.cogsCentavos), emphasize = true)
                            }
                        }
                    }
                }
                item {
                    Actions(
                        status = order.status,
                        enabled = !state.isWorking,
                        onStart = viewModel::start,
                        onComplete = viewModel::complete,
                        onCancel = { cancelling = true },
                        onRefund = { refunding = true }
                    )
                }
            }
        }
    }

    if (cancelling && detail != null) {
        ReasonDialog(
            title = "Cancel order?",
            explanation = "The ${formatPeso(detail.order.totalCentavos)} payment is recorded as refunded. No stock was used yet.",
            confirmLabel = "Cancel order",
            showReturnStock = false,
            onConfirm = { reason, _ -> cancelling = false; viewModel.cancel(reason) },
            onDismiss = { cancelling = false }
        )
    }
    if (refunding && detail != null) {
        ReasonDialog(
            title = "Refund order?",
            explanation = "Refunds the full ${formatPeso(detail.order.totalCentavos)}. The order stays in history, marked refunded.",
            confirmLabel = "Refund",
            showReturnStock = true,
            onConfirm = { reason, returnStock -> refunding = false; viewModel.refund(reason, returnStock) },
            onDismiss = { refunding = false }
        )
    }
}

private fun Instant.local(): String = atZone(ZoneId.systemDefault()).format(ORDER_DATE_TIME)

@Composable
private fun HeaderCard(detail: OrderDetail) {
    val order = detail.order
    CoffeeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(shortOrderNumber(order.orderNumber), style = MaterialTheme.typography.headlineSmall)
                OrderStatusChip(order.status)
            }
            Text(
                "No. ${order.orderNumber} · taken ${order.createdAt.local()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            order.completedAt?.let { Text("Completed ${it.local()}", style = MaterialTheme.typography.bodySmall) }
            order.cancelledAt?.let { Text("Cancelled ${it.local()}", style = MaterialTheme.typography.bodySmall) }
            if (order.note.isNotBlank()) Text("Note: ${order.note}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ItemsCard(detail: OrderDetail) {
    val order = detail.order
    CoffeeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            detail.lines.forEach { LineRow(it) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow("Subtotal", formatPeso(order.subtotalCentavos))
            if (order.loyaltyRewardCentavos > 0) {
                DetailRow("Loyalty free drink", "−${formatPeso(order.loyaltyRewardCentavos)}")
            }
            if (order.discountCentavos > 0) {
                DetailRow("Discount (${order.discountReason})", "−${formatPeso(order.discountCentavos)}")
            }
            DetailRow("Total", formatPeso(order.totalCentavos), emphasize = true)
        }
    }
}

@Composable
private fun LoyaltyCard(customer: CustomerSummary, entries: List<LoyaltyEntryEntity>, onOpenCustomer: (Long) -> Unit) {
    val isDeleted = customer.customer.isDeleted
    CoffeeCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (isDeleted) Modifier else Modifier.clickable { onOpenCustomer(customer.customer.id) })
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DetailRow("Customer", customer.customer.name, emphasize = true)
            entries.forEach { entry ->
                DetailRow(entry.type.label, (if (entry.stampsChange > 0) "+" else "") + "${entry.stampsChange} stamps")
            }
            if (entries.isEmpty()) {
                Text(
                    "Stamps are added when the order is handed over.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LineRow(detail: OrderLineDetail) {
    val line = detail.line
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${line.quantity}× ${line.productName}" + if (line.variationName.isNotBlank()) " · ${line.variationName}" else "",
                style = MaterialTheme.typography.bodyLarge
            )
            detail.addOns.forEach {
                Text(
                    "+ ${if (it.quantityPerItem > 1) "${it.quantityPerItem}× " else ""}${it.addOnName} (${formatPeso(it.priceCentavos)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (line.note.isNotBlank()) {
                Text("“${line.note}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(formatPeso(line.lineTotalCentavos), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PaymentCard(detail: OrderDetail) {
    Column {
        SectionHeader("Payment")
        CoffeeCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                detail.payments.forEach { payment ->
                    DetailRow("Paid by ${payment.method.label}", formatPeso(payment.amountCentavos))
                    payment.cashReceivedCentavos?.let { DetailRow("Cash received", formatPeso(it)) }
                    payment.changeCentavos?.let { DetailRow("Change", formatPeso(it)) }
                    if (payment.reference.isNotBlank()) DetailRow("Reference", payment.reference)
                }
                detail.refunds.forEach { refund ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailRow("Refunded (${refund.method.label})", "−${formatPeso(refund.amountCentavos)}", emphasize = true)
                    Text(
                        "${refund.reason} · ${refund.refundedAt.local()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Actions(
    status: OrderStatus,
    enabled: Boolean,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onRefund: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status == OrderStatus.PENDING) {
            OutlinedButton(onClick = onStart, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Start making")
            }
        }
        if (status.isActive) {
            Button(onClick = onComplete, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Complete (handed over)")
            }
            OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Cancel order", color = MaterialTheme.colorScheme.error)
            }
        }
        if (status == OrderStatus.COMPLETED) {
            OutlinedButton(onClick = onRefund, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Refund order", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, emphasize: Boolean = false) {
    Row {
        Text(
            label,
            style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (emphasize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReasonDialog(
    title: String,
    explanation: String,
    confirmLabel: String,
    showReturnStock: Boolean,
    onConfirm: (reason: String, returnStock: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    var returnStock by rememberSaveable { mutableStateOf(false) }
    FormDialog(
        title = title,
        confirmLabel = confirmLabel,
        confirmEnabled = reason.isNotBlank(),
        onConfirm = { onConfirm(reason, returnStock) },
        onDismiss = onDismiss
    ) {
        Text(explanation, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Reason") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        if (showReturnStock) {
            Row(
                Modifier.fillMaxWidth().clickable { returnStock = !returnStock }.heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Return ingredients to stock")
                    Text(
                        "Only if nothing was used, e.g. the drink was never made",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = returnStock, onCheckedChange = { returnStock = it })
            }
        }
    }
}
