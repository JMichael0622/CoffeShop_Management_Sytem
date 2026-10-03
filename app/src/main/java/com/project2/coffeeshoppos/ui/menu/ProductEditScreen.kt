package com.project2.coffeeshoppos.ui.menu

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.project2.coffeeshoppos.domain.DEFAULT_SIZE_NAME
import com.project2.coffeeshoppos.domain.SizeFormInput
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.ConfirmDialog
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.InfoBanner
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.StatusChip
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.util.formatPeso

@Composable
fun ProductEditScreen(
    onClose: () -> Unit,
    onEditRecipe: (variationId: Long) -> Unit,
    viewModel: ProductEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }

    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.isFinished) { if (state.isFinished) onClose() }

    // Ask before throwing away unsaved edits, for both the Back button and the system back gesture.
    val requestClose = { if (state.hasUnsavedChanges) confirmDiscard = true else onClose() }
    BackHandler(enabled = state.hasUnsavedChanges && !state.isFinished) { confirmDiscard = true }

    SubScreenScaffold(
        title = if (state.isNew) "New product" else "Edit product",
        onBack = requestClose,
        snackbarHostState = snackbarHostState,
        actions = {
            if (!state.isLoading && !state.notFound) {
                TextButton(onClick = viewModel::save, enabled = !state.isSaving) { Text("Save") }
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.notFound -> Box(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        icon = R.drawable.ic_cafe,
                        title = "Product not found",
                        message = "It may have been removed. Go back to the menu."
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
                        text = "This product is archived and hidden from the POS. Restore it below to sell it again."
                    )
                }
                DetailsSection(state, viewModel)
                SizesSection(state, viewModel)
                AddOnsSection(state, viewModel)
                RecipesSection(state, onEditRecipe)
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(if (state.isSaving) "Saving…" else "Save product")
                }
                if (!state.isNew) {
                    OutlinedButton(
                        onClick = { if (state.isArchived) viewModel.setArchived(false) else confirmArchive = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_archive), contentDescription = null)
                        Text(
                            if (state.isArchived) "Restore product" else "Archive product",
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
            message = "Your edits to this product have not been saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = { confirmDiscard = false; onClose() },
            onDismiss = { confirmDiscard = false }
        )
    }
    if (confirmArchive) {
        ConfirmDialog(
            title = "Archive \"${state.form.name}\"?",
            message = "It will be hidden from the POS and menu. Past orders keep their records, " +
                "and you can restore it any time with \"Show archived\".",
            confirmLabel = "Archive",
            onConfirm = { confirmArchive = false; viewModel.setArchived(true) },
            onDismiss = { confirmArchive = false }
        )
    }
}

@Composable
private fun DetailsSection(state: ProductEditUiState, viewModel: ProductEditViewModel) {
    val form = state.form
    OutlinedTextField(
        value = form.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Product name") },
        isError = state.errors.name != null,
        supportingText = state.errors.name?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
    CategoryPicker(state, viewModel)
    OutlinedTextField(
        value = form.description,
        onValueChange = viewModel::onDescriptionChange,
        label = { Text("Description (optional)") },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth()
    )
    CoffeeCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable { viewModel.onAvailableChange(!form.isAvailable) }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Available to sell", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Turn off when sold out for the day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = form.isAvailable, onCheckedChange = viewModel::onAvailableChange)
        }
    }
}

@Composable
private fun CategoryPicker(state: ProductEditUiState, viewModel: ProductEditViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.categories.firstOrNull { it.id == state.form.categoryId }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    selected?.name ?: "No category",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                painterResource(R.drawable.ic_arrow_down),
                contentDescription = "Choose category",
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("No category") },
                onClick = { viewModel.onCategoryChange(null); expanded = false }
            )
            state.selectableCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(if (category.isArchived) "${category.name} (archived)" else category.name) },
                    onClick = { viewModel.onCategoryChange(category.id); expanded = false }
                )
            }
        }
    }
    if (state.categories.none { !it.isArchived }) {
        Text(
            "No categories yet. Add them from the Menu screen's categories button.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SizesSection(state: ProductEditUiState, viewModel: ProductEditViewModel) {
    val sizes = state.form.sizes
    SectionHeader("Sizes & prices")
    Text(
        if (sizes.size == 1) "One price? Leave the size name blank. Add sizes for e.g. 12 oz and 16 oz."
        else "The first size is the default on the POS.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    sizes.forEach { size ->
        SizeRow(
            size = size,
            single = sizes.size == 1,
            error = state.errors.sizes[size.key],
            onNameChange = { viewModel.onSizeNameChange(size.key, it) },
            onPriceChange = { viewModel.onSizePriceChange(size.key, it) },
            onRemove = { viewModel.onRemoveSize(size.key) }
        )
    }
    OutlinedButton(onClick = viewModel::onAddSize, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
        Text("Add size", modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SizeRow(
    size: SizeFormInput,
    single: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = size.name,
                onValueChange = onNameChange,
                label = { Text(if (single) "Size (optional)" else "Size") },
                placeholder = { Text(if (single) "Regular" else "e.g. 16 oz") },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.weight(1.1f)
            )
            OutlinedTextField(
                value = size.priceText,
                onValueChange = onPriceChange,
                label = { Text("Price") },
                prefix = { Text("₱") },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f)
            )
            if (!single) {
                IconButton(onClick = onRemove) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove size ${size.name}")
                }
            }
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddOnsSection(state: ProductEditUiState, viewModel: ProductEditViewModel) {
    SectionHeader("Add-ons offered")
    val addOns = state.selectableAddOns
    if (addOns.isEmpty()) {
        Text(
            "No add-ons yet. Create them (e.g. Extra shot, Oat milk) from the Menu screen's add-ons button.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Text(
        "Tap to choose which add-ons can be ordered with this product.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        addOns.forEach { addOn ->
            FilterChip(
                selected = addOn.id in state.form.addOnIds,
                onClick = { viewModel.onToggleAddOn(addOn.id) },
                label = { Text("${addOn.name} +${formatPeso(addOn.priceCentavos)}") },
                trailingIcon = if (addOn.isArchived) {
                    { StatusChip("Archived") }
                } else null
            )
        }
    }
}

@Composable
private fun RecipesSection(state: ProductEditUiState, onEditRecipe: (Long) -> Unit) {
    SectionHeader("Recipes")
    // Only sizes that are already saved have an id a recipe can belong to.
    val savedSizes = state.form.sizes.filter { it.id != 0L }
    Text(
        if (savedSizes.isEmpty()) "Save the product first, then set the ingredients each size uses."
        else "The ingredients each size uses, so sales take stock out automatically.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    savedSizes.forEach { size ->
        OutlinedButton(
            onClick = { onEditRecipe(size.id) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(
                "Recipe for ${size.name.ifBlank { DEFAULT_SIZE_NAME }}",
                modifier = Modifier.weight(1f)
            )
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}
