package com.project2.coffeeshoppos.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.data.local.entity.InventoryMovementEntity
import com.project2.coffeeshoppos.domain.MovementType
import com.project2.coffeeshoppos.domain.formatQuantity
import com.project2.coffeeshoppos.domain.formatQuantityChange
import com.project2.coffeeshoppos.domain.formatUnitCost
import com.project2.coffeeshoppos.domain.stockValueCentavos
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.DatePickDialog
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.FormDialog
import com.project2.coffeeshoppos.ui.components.InfoBanner
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.util.formatPeso
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HISTORY_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.ENGLISH)
private val PURCHASE_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

@Composable
fun IngredientDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: IngredientDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)
    val ingredient = state.ingredient

    SubScreenScaffold(
        title = ingredient?.name ?: "Ingredient",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (ingredient != null) {
                IconButton(onClick = { onEdit(ingredient.id) }) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit ingredient")
                }
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            ingredient == null -> Box(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        icon = R.drawable.ic_nav_inventory,
                        title = "Ingredient not found",
                        message = "It may have been removed. Go back to Inventory."
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (ingredient.isArchived) {
                    item {
                        InfoBanner(
                            icon = R.drawable.ic_archive,
                            text = "Archived. Restore it from Edit to record purchases or stock changes."
                        )
                    }
                }
                item { StockSummaryCard(ingredient) }
                if (!ingredient.isArchived) {
                    item { StockActions(onAction = viewModel::openDialog) }
                }
                item { SectionHeader("Used in recipes") }
                item { UsedIn(state.usedIn) }
                item { SectionHeader("Stock history") }
                if (state.movements.isEmpty()) {
                    item {
                        CoffeeCard(Modifier.fillMaxWidth()) {
                            EmptyState(
                                icon = R.drawable.ic_receipt,
                                title = "No stock recorded yet",
                                message = "Record a purchase or a stock count to start the history."
                            )
                        }
                    }
                } else {
                    items(state.movements, key = { it.id }) { movement ->
                        MovementRow(movement, ingredient)
                    }
                }
            }
        }
    }

    val dialog = state.dialog
    if (dialog != null && ingredient != null) {
        StockDialog(dialog, ingredient, state.today, viewModel)
    }
}

@Composable
private fun StockSummaryCard(ingredient: IngredientEntity) {
    CoffeeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("On hand", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatQuantity(ingredient.quantityOnHandMilli, ingredient.baseUnit),
                style = MaterialTheme.typography.headlineMedium
            )
            IngredientStatusChip(ingredient)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SummaryRow("Average cost", formatUnitCost(ingredient.averageCostCentavosPerUnit, ingredient.baseUnit))
            SummaryRow("Stock value", formatPeso(ingredient.stockValueCentavos))
            SummaryRow(
                "Reorder level",
                if (ingredient.reorderLevelMilli > 0) formatQuantity(ingredient.reorderLevelMilli, ingredient.baseUnit) else "Not set"
            )
            if (ingredient.category.isNotBlank()) SummaryRow("Group", ingredient.category)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StockActions(onAction: (StockAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onAction(StockAction.PURCHASE) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text("Record purchase", modifier = Modifier.padding(start = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAction(StockAction.WASTAGE) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text("Wastage")
            }
            OutlinedButton(onClick = { onAction(StockAction.COUNT) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text("Stock count")
            }
        }
        TextButton(onClick = { onAction(StockAction.CORRECT_COST) }, modifier = Modifier.fillMaxWidth()) {
            Text("Cost entered wrong? Correct cost")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsedIn(names: List<String>) {
    if (names.isEmpty()) {
        Text(
            "Not used in any recipe yet. Add it to recipes from Inventory › Recipes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { SuggestionChip(onClick = {}, label = { Text(it) }, enabled = false) }
    }
}

@Composable
private fun MovementRow(movement: InventoryMovementEntity, ingredient: IngredientEntity) {
    val zone = remember { ZoneId.systemDefault() }
    CoffeeCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(movement.type.label, style = MaterialTheme.typography.titleSmall)
                if (movement.reason.isNotBlank() && movement.reason != movement.type.label) {
                    Text(movement.reason, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    movement.createdAt.atZone(zone).format(HISTORY_TIME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (movement.type == MovementType.COST_CORRECTION) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(formatUnitCost(movement.unitCostCentavosPerUnit, ingredient.baseUnit), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "value ${if (movement.costCentavos >= 0) "+" else "−"}${formatPeso(kotlin.math.abs(movement.costCentavos))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    formatQuantityChange(movement.quantityChangeMilli, ingredient.baseUnit),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (movement.quantityChangeMilli < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${formatPeso(movement.costCentavos)} · now ${formatQuantity(movement.quantityAfterMilli, ingredient.baseUnit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StockDialog(
    dialog: StockDialogState,
    ingredient: IngredientEntity,
    today: LocalDate,
    viewModel: IngredientDetailViewModel,
) {
    var pickingDate by remember { mutableStateOf(false) }
    FormDialog(
        title = dialog.action.title,
        confirmLabel = if (dialog.isSaving) "Saving…" else dialog.action.confirmLabel,
        confirmEnabled = !dialog.isSaving,
        onConfirm = viewModel::saveDialog,
        onDismiss = viewModel::closeDialog
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (dialog.action) {
                    StockAction.PURCHASE -> "Stock you bought. This is a stock purchase, not an operating expense."
                    StockAction.WASTAGE -> "Stock that was spoiled, spilled or expired. It is removed at average cost."
                    StockAction.COUNT -> "Enter what you physically counted. Recorded stock: " +
                        formatQuantity(ingredient.quantityOnHandMilli, ingredient.baseUnit) + "."
                    StockAction.CORRECT_COST -> "Only for fixing a mistake, like a mistyped purchase. The stock on hand is " +
                        "revalued; past orders keep their cost. If the supplier's price went up, record a purchase instead."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (dialog.action != StockAction.CORRECT_COST) {
                QuantityField(
                    value = dialog.quantityText,
                    onValueChange = viewModel::onQuantityChange,
                    unit = dialog.unit,
                    onUnitChange = viewModel::onUnitChange,
                    label = when (dialog.action) {
                        StockAction.PURCHASE -> "Quantity bought"
                        StockAction.WASTAGE -> "Quantity wasted"
                        else -> "Counted quantity"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            when (dialog.action) {
                StockAction.PURCHASE -> {
                    OutlinedTextField(
                        value = dialog.costText,
                        onValueChange = viewModel::onCostChange,
                        label = { Text("Total paid") },
                        prefix = { Text("₱") },
                        supportingText = viewModel.purchaseUnitCostLabel(dialog, ingredient)?.let { { Text("$it for this purchase") } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Bought on " + if (dialog.purchasedOn == today) "today" else dialog.purchasedOn.format(PURCHASE_DATE),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = "Change date")
                    }
                    TextInput(dialog.supplier, viewModel::onSupplierChange, "Supplier (optional)", KeyboardCapitalization.Words)
                    TextInput(dialog.reference, viewModel::onReferenceChange, "Receipt no. (optional)", KeyboardCapitalization.None)
                    TextInput(dialog.note, viewModel::onNoteChange, "Notes (optional)", KeyboardCapitalization.Sentences)
                }
                StockAction.WASTAGE -> TextInput(dialog.note, viewModel::onNoteChange, "What happened?", KeyboardCapitalization.Sentences)
                StockAction.COUNT -> TextInput(dialog.note, viewModel::onNoteChange, "Note (optional)", KeyboardCapitalization.Sentences)
                StockAction.CORRECT_COST -> {
                    Text(
                        "Current cost: " + formatUnitCost(ingredient.averageCostCentavosPerUnit, ingredient.baseUnit),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = dialog.costText,
                        onValueChange = viewModel::onCostChange,
                        label = { Text("Correct cost per ${ingredient.baseUnit.displayUnit.symbol}") },
                        prefix = { Text("₱") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextInput(dialog.note, viewModel::onNoteChange, "What was wrong?", KeyboardCapitalization.Sentences)
                }
            }
            if (dialog.error != null) {
                Text(dialog.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (pickingDate) {
        DatePickDialog(
            initial = dialog.purchasedOn,
            latest = today,
            onPick = viewModel::onDateChange,
            onDismiss = { pickingDate = false }
        )
    }
}

@Composable
private fun TextInput(value: String, onValueChange: (String) -> Unit, label: String, capitalization: KeyboardCapitalization) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
}
