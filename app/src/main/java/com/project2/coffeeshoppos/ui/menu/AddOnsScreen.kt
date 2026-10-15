package com.project2.coffeeshoppos.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.entity.AddOnEntity
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.ConfirmDialog
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.FormDialog
import com.project2.coffeeshoppos.ui.components.InfoBanner
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.StatusChip
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.ui.theme.LocalCoffeeExtraColors
import com.project2.coffeeshoppos.util.formatPeso

@Composable
fun AddOnsScreen(
    onBack: () -> Unit,
    onEditRecipe: (addOnId: Long) -> Unit,
    viewModel: AddOnsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmArchive by remember { mutableStateOf<AddOnEntity?>(null) }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)

    SubScreenScaffold(
        title = "Add-ons",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Add add-on") },
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
                    "Extras like an extra shot or oat milk. Choose which products offer each add-on " +
                        "in the product editor. Tap an add-on to set its recipe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val missingRecipes = state.active.count { it.id !in state.recipeCounts }
            if (missingRecipes > 0) {
                item {
                    InfoBanner(
                        icon = R.drawable.ic_warning,
                        text = "$missingRecipes " + (if (missingRecipes == 1) "add-on has" else "add-ons have") +
                            " no recipe yet, so selling it uses no stock and costs ₱0."
                    )
                }
            }
            item {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    if (state.active.isEmpty() && !state.isLoading) {
                        EmptyState(
                            icon = R.drawable.ic_discount,
                            title = "No add-ons yet",
                            message = "Tap \"Add add-on\" to create one."
                        )
                    } else {
                        Column {
                            state.active.forEachIndexed { index, addOn ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                AddOnRow(
                                    addOn = addOn,
                                    recipeCount = state.recipeCounts[addOn.id] ?: 0,
                                    onClick = { viewModel.openEdit(addOn) },
                                    onAvailableChange = { viewModel.setAvailable(addOn, it) }
                                )
                            }
                        }
                    }
                }
            }
            if (state.archived.isNotEmpty()) {
                item { SectionHeader("Archived") }
                item {
                    CoffeeCard(Modifier.fillMaxWidth()) {
                        Column {
                            state.archived.forEachIndexed { index, addOn ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    modifier = Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${addOn.name} · ${formatPeso(addOn.priceCentavos)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.setArchived(addOn, false) }) { Text("Restore") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.dialog?.let { dialog ->
        FormDialog(
            title = if (dialog.addOn == null) "New add-on" else "Edit add-on",
            confirmLabel = "Save",
            confirmEnabled = !dialog.isSaving && dialog.name.isNotBlank(),
            onConfirm = viewModel::saveDialog,
            onDismiss = viewModel::closeDialog
        ) {
            OutlinedTextField(
                value = dialog.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                placeholder = { Text("e.g. Extra shot") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dialog.priceText,
                onValueChange = viewModel::onPriceChange,
                label = { Text("Price") },
                prefix = { Text("₱") },
                singleLine = true,
                isError = dialog.error != null,
                supportingText = dialog.error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onAvailableChange(!dialog.isAvailable) }
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Available", modifier = Modifier.weight(1f))
                Switch(checked = dialog.isAvailable, onCheckedChange = viewModel::onAvailableChange)
            }
            if (dialog.addOn != null) {
                TextButton(onClick = { viewModel.closeDialog(); onEditRecipe(dialog.addOn.id) }) {
                    Icon(painterResource(R.drawable.ic_nav_inventory), contentDescription = null)
                    Text("Edit recipe", modifier = Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = { confirmArchive = dialog.addOn }) {
                    Icon(painterResource(R.drawable.ic_archive), contentDescription = null)
                    Text("Archive add-on", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    confirmArchive?.let { addOn ->
        ConfirmDialog(
            title = "Archive \"${addOn.name}\"?",
            message = "It will no longer be offered on the POS. Past orders keep their records, " +
                "and you can restore it later.",
            confirmLabel = "Archive",
            onConfirm = { confirmArchive = null; viewModel.setArchived(addOn, true) },
            onDismiss = { confirmArchive = null }
        )
    }
}

@Composable
private fun AddOnRow(addOn: AddOnEntity, recipeCount: Int, onClick: () -> Unit, onAvailableChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(addOn.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "+${formatPeso(addOn.priceCentavos)}" +
                    if (recipeCount > 0) " · $recipeCount " + (if (recipeCount == 1) "ingredient" else "ingredients") else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (recipeCount == 0) StatusChip("No recipe", color = LocalCoffeeExtraColors.current.warning)
            if (!addOn.isAvailable) StatusChip("Unavailable", color = LocalCoffeeExtraColors.current.warning)
        }
        Switch(
            checked = addOn.isAvailable,
            onCheckedChange = onAvailableChange,
            modifier = Modifier.semantics { contentDescription = "${addOn.name} available" }
        )
    }
}
