package com.project2.coffeeshoppos.ui.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.ConfirmDialog
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.InfoBanner
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold

@Composable
fun IngredientEditScreen(
    onClose: () -> Unit,
    viewModel: IngredientEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }

    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.isFinished) { if (state.isFinished) onClose() }

    val requestClose = { if (state.hasUnsavedChanges) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.hasUnsavedChanges && !state.isFinished) { confirmDiscard = true }

    SubScreenScaffold(
        title = if (state.isNew) "New ingredient" else "Edit ingredient",
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
                        icon = R.drawable.ic_nav_inventory,
                        title = "Ingredient not found",
                        message = "It may have been removed. Go back to Inventory."
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
                if (state.isArchived) {
                    InfoBanner(
                        icon = R.drawable.ic_archive,
                        text = "This ingredient is archived. Restore it below to buy or use it again."
                    )
                }
                DetailsSection(state, viewModel)
                if (state.isNew) OpeningStockSection(state, viewModel)
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(if (state.isSaving) "Saving…" else "Save ingredient")
                }
                if (!state.isNew) {
                    OutlinedButton(
                        onClick = { if (state.isArchived) viewModel.setArchived(false) else confirmArchive = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_archive), contentDescription = null)
                        Text(
                            if (state.isArchived) "Restore ingredient" else "Archive ingredient",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your edits to this ingredient have not been saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = { confirmDiscard = false; onClose() },
            onDismiss = { confirmDiscard = false }
        )
    }
    if (confirmArchive) {
        ConfirmDialog(
            title = "Archive \"${state.savedForm.name}\"?",
            message = "It will be hidden from Inventory and can no longer be bought or added to recipes. " +
                "Its stock history is kept, and you can restore it with \"Show archived\".",
            confirmLabel = "Archive",
            onConfirm = { confirmArchive = false; viewModel.setArchived(true) },
            onDismiss = { confirmArchive = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSection(state: IngredientEditUiState, viewModel: IngredientEditViewModel) {
    val form = state.form
    OutlinedTextField(
        value = form.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Ingredient name") },
        placeholder = { Text("e.g. Espresso beans") },
        isError = state.errors.name != null,
        supportingText = state.errors.name?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = form.category,
        onValueChange = viewModel::onCategoryChange,
        label = { Text("Group (optional)") },
        placeholder = { Text("e.g. Dairy, Packaging") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )

    SectionHeader("Measured by")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            MeasureUnit.GRAM to "Weight (g, kg)",
            MeasureUnit.MILLILITER to "Volume (ml, L)",
            MeasureUnit.PIECE to "Count (pc)"
        ).forEach { (unit, label) ->
            FilterChip(
                selected = form.baseUnit == unit,
                onClick = { viewModel.onBaseUnitChange(unit) },
                enabled = state.canChangeUnit || form.baseUnit == unit,
                label = { Text(label) }
            )
        }
    }
    Text(
        if (state.canChangeUnit) "Stock is kept in ${form.baseUnit.symbol}. This cannot change once stock is recorded."
        else "Stock is kept in ${form.baseUnit.symbol}. The unit is locked because stock or recipes already use it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    SectionHeader("Reorder level")
    QuantityField(
        value = form.reorderText,
        onValueChange = viewModel::onReorderChange,
        unit = form.reorderUnit,
        onUnitChange = viewModel::onReorderUnitChange,
        label = "Warn me at (optional)",
        error = state.errors.reorder,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "The ingredient is flagged as low stock when it drops to this amount.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OpeningStockSection(state: IngredientEditUiState, viewModel: IngredientEditViewModel) {
    val form = state.form
    SectionHeader("Stock you have now")
    Text(
        "Optional. Enter what is already in the shop and what it cost, so stock value and recipe costs are right " +
            "from the start. Leave the cost blank if you don't know it; your next purchase will set it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    QuantityField(
        value = form.openingText,
        onValueChange = viewModel::onOpeningChange,
        unit = form.openingUnit,
        onUnitChange = viewModel::onOpeningUnitChange,
        label = "Quantity on hand",
        error = state.errors.opening,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = form.openingCostText,
        onValueChange = viewModel::onOpeningCostChange,
        label = { Text("Total cost of that stock") },
        prefix = { Text("₱") },
        singleLine = true,
        isError = state.errors.openingCost != null,
        supportingText = state.errors.openingCost?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth()
    )
}
