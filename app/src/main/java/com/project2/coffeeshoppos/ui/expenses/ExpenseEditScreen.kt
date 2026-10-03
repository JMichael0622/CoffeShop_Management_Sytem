package com.project2.coffeeshoppos.ui.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.ConfirmDialog
import com.project2.coffeeshoppos.ui.components.DatePickDialog
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.FormDialog
import com.project2.coffeeshoppos.ui.components.InfoBanner
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import java.time.LocalDate

private enum class PickingDate { INCURRED, PAID }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditScreen(
    onClose: () -> Unit,
    viewModel: ExpenseEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<PickingDate?>(null) }

    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.isFinished) { if (state.isFinished) onClose() }
    val requestClose = { if (state.hasUnsavedChanges) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.hasUnsavedChanges && !state.isFinished) { confirmDiscard = true }

    SubScreenScaffold(
        title = if (state.isNew) "Record expense" else "Edit expense",
        onBack = requestClose,
        snackbarHostState = snackbarHostState,
        actions = {
            if (!state.isLoading && !state.notFound) {
                TextButton(onClick = viewModel::save, enabled = !state.isSaving) { Text("Save") }
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.notFound -> Box(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    EmptyState(icon = R.drawable.ic_nav_expenses, title = "Not found", message = "It may have been deleted.")
                }
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val form = state.form
                state.recurringLabel?.let { InfoBanner(icon = R.drawable.ic_receipt, text = it) }
                OutlinedTextField(
                    value = form.description,
                    onValueChange = viewModel::onDescription,
                    label = { Text("What was it for?") },
                    placeholder = { Text("e.g. Electricity bill") },
                    isError = state.errors.description != null,
                    supportingText = state.errors.description?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.amountText,
                    onValueChange = viewModel::onAmount,
                    label = { Text("Amount") },
                    prefix = { Text("₱") },
                    isError = state.errors.amount != null,
                    supportingText = state.errors.amount?.let { { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                DateButton("Date of expense", form.incurredDate, state.today) { picking = PickingDate.INCURRED }

                SectionHeader("Category")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseCategory.entries.forEach { category ->
                        FilterChip(selected = form.category == category, onClick = { viewModel.onCategory(category) }, label = { Text(category.label) })
                    }
                }

                CoffeeCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.clickable { viewModel.onPaid(!form.isPaid) }.padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Already paid", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (form.isPaid) "Paid" else "Owed. It stays on the unpaid list until marked paid.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = form.isPaid, onCheckedChange = viewModel::onPaid)
                    }
                }
                if (form.isPaid) {
                    DateButton("Paid on", form.paidDate, state.today) { picking = PickingDate.PAID }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaymentMethod.entries.forEach { method ->
                            FilterChip(selected = form.method == method, onClick = { viewModel.onMethod(method) }, label = { Text(method.label) })
                        }
                    }
                    OutlinedTextField(
                        value = form.reference,
                        onValueChange = viewModel::onReference,
                        label = { Text("Receipt or reference no. (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = viewModel::onNotes,
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = viewModel::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (state.isSaving) "Saving…" else "Save expense")
                }
                if (!state.isNew) {
                    OutlinedButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                        Text("Delete expense", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    when (picking) {
        PickingDate.INCURRED -> DatePickDialog(
            initial = state.form.incurredDate,
            onPick = viewModel::onIncurredDate,
            onDismiss = { picking = null }
        )
        PickingDate.PAID -> DatePickDialog(
            initial = state.form.paidDate,
            latest = state.today,
            onPick = viewModel::onPaidDate,
            onDismiss = { picking = null }
        )
        null -> Unit
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "This expense has not been saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = { confirmDiscard = false; onClose() },
            onDismiss = { confirmDiscard = false }
        )
    }
    if (deleting) {
        var reason by rememberSaveable { mutableStateOf("") }
        FormDialog(
            title = "Delete this expense?",
            confirmLabel = "Delete",
            confirmEnabled = reason.isNotBlank(),
            onConfirm = { deleting = false; viewModel.delete(reason) },
            onDismiss = { deleting = false }
        ) {
            Text(
                "It will be removed from expenses and reports. A copy is kept in the audit log.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason") },
                placeholder = { Text("e.g. Entered twice") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DateButton(label: String, date: LocalDate, today: LocalDate, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.small) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (date == today) "Today" else date.format(DAY_WITH_YEAR),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null)
    }
}
