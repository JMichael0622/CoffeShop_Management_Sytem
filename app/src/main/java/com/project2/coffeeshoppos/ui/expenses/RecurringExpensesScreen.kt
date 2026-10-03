package com.project2.coffeeshoppos.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.entity.RecurringExpenseTemplateEntity
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.RecurrenceFrequency
import com.project2.coffeeshoppos.domain.dueDayLabel
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.FormDialog
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.util.formatPeso

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun RecurringExpensesScreen(
    onBack: () -> Unit,
    viewModel: RecurringExpensesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)

    SubScreenScaffold(
        title = "Recurring expenses",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Add reminder") },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Reminders for regular costs like rent, salaries and Wi-Fi. When one is due it appears on the " +
                        "Expenses tab, ready to record with one tap. Nothing is recorded automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    if (state.active.isEmpty() && !state.isLoading) {
                        EmptyState(icon = R.drawable.ic_receipt, title = "No reminders yet", message = "Tap \"Add reminder\" to create one.")
                    } else {
                        TemplateList(state.active, onClick = viewModel::openEdit)
                    }
                }
            }
            if (state.stopped.isNotEmpty()) {
                item { SectionHeader("Stopped") }
                item {
                    CoffeeCard(Modifier.fillMaxWidth()) {
                        Column {
                            state.stopped.forEachIndexed { index, template ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${template.description} · ${formatPeso(template.amountCentavos)}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.setActive(template, true) }) { Text("Resume") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.dialog?.let { TemplateDialog(it, viewModel) }
}

@Composable
private fun TemplateList(templates: List<RecurringExpenseTemplateEntity>, onClick: (RecurringExpenseTemplateEntity) -> Unit) {
    Column {
        templates.forEachIndexed { index, template ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clickable { onClick(template) }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(template.description, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${template.frequency.label} · ${dueDayLabel(template.frequency, template.dueDay)} · ${template.category.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(formatPeso(template.amountCentavos), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateDialog(dialog: TemplateDialogState, viewModel: RecurringExpensesViewModel) {
    FormDialog(
        title = if (dialog.template == null) "New reminder" else "Edit reminder",
        confirmLabel = "Save",
        confirmEnabled = !dialog.isSaving,
        onConfirm = viewModel::save,
        onDismiss = viewModel::closeDialog
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = dialog.description,
                onValueChange = viewModel::onDescription,
                label = { Text("Expense") },
                placeholder = { Text("e.g. Rent") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dialog.amountText,
                onValueChange = viewModel::onAmount,
                label = { Text("Usual amount") },
                prefix = { Text("₱") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Text("Repeats", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceFrequency.entries.forEach { frequency ->
                    FilterChip(selected = dialog.frequency == frequency, onClick = { viewModel.onFrequency(frequency) }, label = { Text(frequency.label) })
                }
            }
            if (dialog.frequency == RecurrenceFrequency.MONTHLY) {
                OutlinedTextField(
                    value = dialog.dayOfMonthText,
                    onValueChange = viewModel::onDayOfMonth,
                    label = { Text("Due on day (1–31)") },
                    supportingText = { Text("In shorter months, a day past the end means the last day.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEKDAYS.forEachIndexed { index, day ->
                        FilterChip(selected = dialog.dayOfWeek == index + 1, onClick = { viewModel.onDayOfWeek(index + 1) }, label = { Text(day) })
                    }
                }
            }
            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExpenseCategory.entries.forEach { category ->
                    FilterChip(selected = dialog.category == category, onClick = { viewModel.onCategory(category) }, label = { Text(category.label) })
                }
            }
            Text("Usually paid by (optional)", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaymentMethod.entries.forEach { method ->
                    FilterChip(selected = dialog.method == method, onClick = { viewModel.onMethod(method) }, label = { Text(method.label) })
                }
            }
            OutlinedTextField(
                value = dialog.notes,
                onValueChange = viewModel::onNotes,
                label = { Text("Notes (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (dialog.error != null) {
                Text(dialog.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (dialog.template != null) {
                TextButton(onClick = { viewModel.setActive(dialog.template, false) }) {
                    Text("Stop this reminder", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
