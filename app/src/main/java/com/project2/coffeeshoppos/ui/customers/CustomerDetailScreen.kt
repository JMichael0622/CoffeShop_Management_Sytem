package com.project2.coffeeshoppos.ui.customers

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.entity.LoyaltyEntryEntity
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import com.project2.coffeeshoppos.data.repository.shortOrderNumber
import com.project2.coffeeshoppos.domain.LoyaltyMath
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.ConfirmDialog
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.FormDialog
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.ui.orders.ORDER_DATE_TIME
import com.project2.coffeeshoppos.ui.orders.OrderStatusChip
import com.project2.coffeeshoppos.ui.pos.StampProgress
import com.project2.coffeeshoppos.util.formatPeso
import java.time.Instant
import java.time.ZoneId

private fun Instant.local(): String = atZone(ZoneId.systemDefault()).format(ORDER_DATE_TIME)

/** One customer: stamps, visits, history, and editing. */
@Composable
fun CustomerDetailScreen(
    onBack: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    viewModel: CustomerDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onBack() }

    val summary = state.summary
    SubScreenScaffold(
        title = summary?.customer?.name ?: "Customer",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (summary != null) {
                IconButton(onClick = viewModel::openEdit) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit customer")
                }
            }
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@SubScreenScaffold
        }
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    EmptyState(icon = R.drawable.ic_person, title = "Customer not found", message = "Go back to the customer list.")
                }
            }
            return@SubScreenScaffold
        }
        val customer = summary.customer
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customer.phone?.let { Text(LoyaltyMath.formatPhone(it), style = MaterialTheme.typography.titleMedium) }
                        if (customer.note.isNotBlank()) {
                            Text(customer.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StampProgress(summary.stamps, state.rules)
                        OutlinedButton(onClick = viewModel::openAdjust, modifier = Modifier.fillMaxWidth()) {
                            Text("Add or remove stamps")
                        }
                    }
                }
            }
            item {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatRow("Visits", summary.visits.toString())
                        StatRow("Total spent", formatPeso(summary.totalSpentCentavos))
                        StatRow("Last visit", summary.lastVisitAt?.lastVisitLabel() ?: "—")
                        StatRow("Usual order", state.favorite ?: "—")
                        StatRow("Customer since", customer.createdAt.lastVisitLabel())
                    }
                }
            }
            if (state.orders.isNotEmpty()) {
                item { SectionHeader("Recent orders") }
                item {
                    CoffeeCard(Modifier.fillMaxWidth()) {
                        Column {
                            state.orders.forEachIndexed { index, order ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                OrderRow(order, onClick = { onOpenOrder(order.id) })
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Stamp history") }
            item {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    if (state.entries.isEmpty()) {
                        EmptyState(icon = R.drawable.ic_star, title = "No stamps yet", message = "Stamps are added when this customer's orders are handed over.")
                    } else {
                        Column {
                            state.entries.forEachIndexed { index, entry ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                EntryRow(entry)
                            }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete customer", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    state.editDialog?.let { dialog ->
        CustomerFormDialog(
            title = "Edit customer",
            dialog = dialog,
            onNameChange = viewModel::onNameChange,
            onPhoneChange = viewModel::onPhoneChange,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::closeEdit
        )
    }

    state.adjustDialog?.let { dialog ->
        FormDialog(
            title = "Add or remove stamps",
            confirmLabel = "Save",
            confirmEnabled = !dialog.isSaving && dialog.countText.isNotBlank() && dialog.reason.isNotBlank(),
            onConfirm = viewModel::saveAdjust,
            onDismiss = viewModel::closeAdjust
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = dialog.add, onClick = { viewModel.onAdjustDirection(true) }, label = { Text("Add") })
                FilterChip(selected = !dialog.add, onClick = { viewModel.onAdjustDirection(false) }, label = { Text("Remove") })
            }
            OutlinedTextField(
                value = dialog.countText,
                onValueChange = viewModel::onAdjustCount,
                label = { Text("Stamps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = dialog.reason,
                onValueChange = viewModel::onAdjustReason,
                label = { Text("Reason") },
                placeholder = { Text("e.g. carried over paper card") },
                singleLine = true,
                isError = dialog.error != null,
                supportingText = dialog.error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${summary?.customer?.name ?: "customer"}?",
            message = "Their name, number and stamps are removed for good. Past orders stay in your sales records " +
                "but no longer show who ordered.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { confirmDelete = false; viewModel.delete() },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OrderRow(order: OrderEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("${shortOrderNumber(order.orderNumber)} · ${formatPeso(order.totalCentavos)}", style = MaterialTheme.typography.bodyLarge)
            Text(order.createdAt.local(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OrderStatusChip(order.status)
    }
}

@Composable
private fun EntryRow(entry: LoyaltyEntryEntity) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.type.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${entry.reason} · ${entry.createdAt.local()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            (if (entry.stampsChange > 0) "+" else "") + entry.stampsChange,
            style = MaterialTheme.typography.titleMedium,
            color = if (entry.stampsChange >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
