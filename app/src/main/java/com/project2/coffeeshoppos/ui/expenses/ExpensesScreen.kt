package com.project2.coffeeshoppos.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.entity.ExpenseEntity
import com.project2.coffeeshoppos.domain.ExpenseReminder
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.PaymentStatus
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.DatePickDialog
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.FormDialog
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.StatusChip
import com.project2.coffeeshoppos.ui.theme.LocalCoffeeExtraColors
import com.project2.coffeeshoppos.util.formatPeso

/** The Expenses tab: a month of operating expenses, what is still unpaid, and recurring ones due. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onRecordReminder: (templateId: Long) -> Unit,
    onOpenRecurring: () -> Unit,
    viewModel: ExpensesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                actions = {
                    TextButton(onClick = onOpenRecurring) {
                        Icon(painterResource(R.drawable.ic_receipt), contentDescription = null)
                        Text("Recurring", modifier = Modifier.padding(start = 8.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Record expense") },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.onMonth(state.month.minusMonths(1)) }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Previous month")
                    }
                    Text(
                        state.month.format(MONTH_TITLE),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.onMonth(state.month.plusMonths(1)) }, enabled = state.month < state.currentMonth) {
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next month")
                    }
                }
            }
            item { MonthSummary(state) }

            if (state.reminders.isNotEmpty()) {
                item { SectionHeader("Due this period") }
                item {
                    CoffeeCard(Modifier.fillMaxWidth()) {
                        Column {
                            state.reminders.forEachIndexed { index, reminder ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                ReminderRow(reminder, onRecord = { onRecordReminder(reminder.templateId) })
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(
                        selected = !state.unpaidOnly,
                        onClick = { viewModel.onUnpaidOnly(false) },
                        label = { Text(state.month.format(MONTH_SHORT)) }
                    )
                    FilterChip(
                        selected = state.unpaidOnly,
                        onClick = { viewModel.onUnpaidOnly(true) },
                        label = { Text("All unpaid (${state.unpaid.size})") }
                    )
                }
            }
            if (state.listed.isEmpty()) {
                item {
                    CoffeeCard(Modifier.fillMaxWidth()) {
                        EmptyState(
                            icon = R.drawable.ic_nav_expenses,
                            title = if (state.unpaidOnly) "Nothing unpaid" else "No expenses this month",
                            message = if (state.unpaidOnly) "Every recorded expense is paid."
                            else "Record rent, utilities, salaries and other running costs here. Stock purchases go in Inventory."
                        )
                    }
                }
            }
            items(state.listed, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    showYear = state.unpaidOnly,
                    onClick = { onOpenExpense(expense.id) },
                    onMarkPaid = { viewModel.openMarkPaid(expense) }
                )
            }
        }
    }

    state.markPaid?.let { MarkPaidDialog(it, state, viewModel) }
}

@Composable
private fun MonthSummary(state: ExpensesUiState) {
    CoffeeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Operating expenses", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatPeso(state.monthTotalCentavos), style = MaterialTheme.typography.headlineMedium)
            if (state.monthUnpaidCentavos > 0) {
                Text(
                    "${formatPeso(state.monthUnpaidCentavos)} not yet paid",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalCoffeeExtraColors.current.warning
                )
            }
            val total = state.monthTotalCentavos
            state.byCategory.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(item.category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(formatPeso(item.amountCentavos), style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(
                        progress = { if (total > 0) item.amountCentavos.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(reminder: ExpenseReminder, onRecord: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(reminder.description, style = MaterialTheme.typography.titleSmall)
            Text(
                "${formatPeso(reminder.amountCentavos)} · ${reminder.category.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusChip(
                (if (reminder.isOverdue) "Overdue since " else "Due ") + reminder.dueDate.format(DAY_SHORT),
                color = if (reminder.isOverdue) MaterialTheme.colorScheme.error else LocalCoffeeExtraColors.current.warning
            )
        }
        OutlinedButton(onClick = onRecord) { Text("Record") }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseEntity, showYear: Boolean, onClick: () -> Unit, onMarkPaid: () -> Unit) {
    val unpaid = expense.paymentStatus == PaymentStatus.UNPAID
    CoffeeCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(expense.description, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        expense.category.label,
                        expense.incurredDate.format(if (showYear) DAY_WITH_YEAR else DAY_SHORT),
                        expense.paymentMethod?.label
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unpaid) StatusChip("Unpaid", color = LocalCoffeeExtraColors.current.warning)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatPeso(expense.amountCentavos), style = MaterialTheme.typography.titleMedium)
                if (unpaid) TextButton(onClick = onMarkPaid) { Text("Mark paid") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkPaidDialog(dialog: MarkPaidState, state: ExpensesUiState, viewModel: ExpensesViewModel) {
    var pickingDate by remember { mutableStateOf(false) }
    FormDialog(
        title = "Mark as paid",
        confirmLabel = "Mark paid",
        onConfirm = viewModel::confirmMarkPaid,
        onDismiss = viewModel::closeMarkPaid
    ) {
        Text(
            "${dialog.expense.description} · ${formatPeso(dialog.expense.amountCentavos)}",
            style = MaterialTheme.typography.bodyMedium
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            PaymentMethod.entries.forEach { method ->
                FilterChip(selected = dialog.method == method, onClick = { viewModel.onMarkPaidMethod(method) }, label = { Text(method.label) })
            }
        }
        OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Paid on " + if (dialog.paidDate == state.today) "today" else dialog.paidDate.format(DAY_WITH_YEAR))
        }
    }
    if (pickingDate) {
        DatePickDialog(initial = dialog.paidDate, latest = state.today, onPick = viewModel::onMarkPaidDate, onDismiss = { pickingDate = false })
    }
}
