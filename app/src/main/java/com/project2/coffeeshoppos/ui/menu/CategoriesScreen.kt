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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.entity.CategoryEntity
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.ConfirmDialog
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.FormDialog
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmArchive by remember { mutableStateOf<CategoryEntity?>(null) }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)

    SubScreenScaffold(
        title = "Categories",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Add category") },
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
                    "Categories appear on the POS in this order. Use the arrows to reorder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    if (state.active.isEmpty() && !state.isLoading) {
                        EmptyState(
                            icon = R.drawable.ic_category,
                            title = "No categories yet",
                            message = "Add groups like Coffee, Non-coffee, and Pastries."
                        )
                    } else {
                        Column {
                            state.active.forEachIndexed { index, category ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                ActiveCategoryRow(
                                    category = category,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < state.active.lastIndex,
                                    onEdit = { viewModel.openEdit(category) },
                                    onMove = { up -> viewModel.move(category, up) }
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
                            state.archived.forEachIndexed { index, category ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    modifier = Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        category.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.setArchived(category, false) }) { Text("Restore") }
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
            title = if (dialog.category == null) "New category" else "Edit category",
            confirmLabel = "Save",
            confirmEnabled = !dialog.isSaving && dialog.name.isNotBlank(),
            onConfirm = viewModel::saveDialog,
            onDismiss = viewModel::closeDialog
        ) {
            OutlinedTextField(
                value = dialog.name,
                onValueChange = viewModel::onDialogNameChange,
                label = { Text("Category name") },
                singleLine = true,
                isError = dialog.error != null,
                supportingText = dialog.error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )
            if (dialog.category != null) {
                TextButton(onClick = { confirmArchive = dialog.category }) {
                    Icon(painterResource(R.drawable.ic_archive), contentDescription = null)
                    Text("Archive category", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    confirmArchive?.let { category ->
        ConfirmDialog(
            title = "Archive \"${category.name}\"?",
            message = "It will be hidden from the POS. You can restore it later. " +
                "A category can only be archived once it has no active products.",
            confirmLabel = "Archive",
            onConfirm = { confirmArchive = null; viewModel.setArchived(category, true) },
            onDismiss = { confirmArchive = null }
        )
    }
}

@Composable
private fun ActiveCategoryRow(
    category: CategoryEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (up: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onEdit)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            category.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
            Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = "Move ${category.name} up")
        }
        IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
            Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = "Move ${category.name} down")
        }
        IconButton(onClick = onEdit) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit ${category.name}")
        }
    }
}
