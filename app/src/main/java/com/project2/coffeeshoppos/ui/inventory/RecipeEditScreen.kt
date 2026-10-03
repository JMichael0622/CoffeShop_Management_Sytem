package com.project2.coffeeshoppos.ui.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.domain.costPercent
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.ConfirmDialog
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.util.formatPeso

@Composable
fun RecipeEditScreen(
    onClose: () -> Unit,
    onAddIngredient: () -> Unit,
    viewModel: RecipeEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.isFinished) { if (state.isFinished) onClose() }

    val requestClose = { if (state.hasUnsavedChanges) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.hasUnsavedChanges && !state.isFinished) { confirmDiscard = true }

    SubScreenScaffold(
        title = "Recipe",
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
                    EmptyState(
                        icon = R.drawable.ic_cafe,
                        title = "Item not found",
                        message = "This size or add-on may have been removed."
                    )
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
                Text(state.title, style = MaterialTheme.typography.titleLarge)
                CostSummary(state)
                SectionHeader("Ingredients for one")
                if (state.ingredients.none { !it.isArchived }) {
                    Text(
                        "No ingredients yet. Add the ingredients you use first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onAddIngredient) { Text("Add an ingredient") }
                } else {
                    if (state.lines.isEmpty()) {
                        Text(
                            "No ingredients listed. Selling this will not use any stock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.lines.forEach { line -> LineRow(line, state, viewModel) }
                    OutlinedButton(onClick = viewModel::onAddLine, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                        Text("Add ingredient", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(if (state.isSaving) "Saving…" else "Save recipe")
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your edits to this recipe have not been saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = { confirmDiscard = false; onClose() },
            onDismiss = { confirmDiscard = false }
        )
    }
}

@Composable
private fun CostSummary(state: RecipeEditUiState) {
    val cost = state.totalCostCentavos
    CoffeeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CostRow("Selling price", formatPeso(state.priceCentavos))
            CostRow(
                "Ingredient cost",
                formatPeso(cost) + (costPercent(cost, state.priceCentavos)?.let { " ($it%)" } ?: "")
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CostRow("Gross profit each", formatPeso(state.priceCentavos - cost), emphasize = true)
            Text(
                "Based on today's average costs. Discounts and operating expenses are not included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CostRow(label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
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
private fun LineRow(line: RecipeLineForm, state: RecipeEditUiState, viewModel: RecipeEditViewModel) {
    val ingredient = line.ingredientId?.let { state.ingredientsById[it] }
    val error = state.lineErrors[line.key]
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1.3f)) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        ingredient?.let { if (it.isArchived) "${it.name} (archived)" else it.name } ?: "Choose…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = "Choose ingredient", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.selectableIngredients.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${option.name} (${option.baseUnit.symbol})") },
                            onClick = { viewModel.onIngredientChange(line.key, option.id); expanded = false }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = line.quantityText,
                onValueChange = { viewModel.onQuantityChange(line.key, it) },
                label = { Text("Qty") },
                suffix = ingredient?.let { { Text(it.baseUnit.symbol) } },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.onRemoveLine(line.key) }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove ${ingredient?.name ?: "row"}")
            }
        }
        val detail = error ?: state.lineCost(line)?.let { "Costs ${formatPeso(it)}" }
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}
