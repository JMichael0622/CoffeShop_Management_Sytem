package com.project2.coffeeshoppos.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.domain.formatQuantity
import com.project2.coffeeshoppos.domain.formatUnitCost
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.MetricCard
import com.project2.coffeeshoppos.util.formatPeso

/** The Inventory tab: every ingredient with its stock level, value and restock warnings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onAddIngredient: () -> Unit,
    onOpenIngredient: (Long) -> Unit,
    onOpenRecipes: () -> Unit,
    viewModel: InventoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // The app shell already leaves room for the bottom bar; only the status bar is handled here.
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text("Inventory") },
                actions = {
                    TextButton(onClick = onOpenRecipes) {
                        Icon(painterResource(R.drawable.ic_cafe), contentDescription = null)
                        Text("Recipes", modifier = Modifier.padding(start = 8.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddIngredient,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Add ingredient") },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        label = "Stock value",
                        value = formatPeso(state.stockValueCentavos),
                        icon = R.drawable.ic_nav_inventory,
                        supportingText = "At average cost",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Needs restock",
                        value = (state.lowStockCount + state.outOfStockCount).toString(),
                        icon = R.drawable.ic_warning,
                        supportingText = "${state.outOfStockCount} out of stock",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search ingredients") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = !state.needsRestockOnly,
                            onClick = { viewModel.onNeedsRestockOnlyChange(false) },
                            label = { Text("All") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.needsRestockOnly,
                            onClick = { viewModel.onNeedsRestockOnlyChange(true) },
                            label = { Text("Needs restock") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = state.showArchived,
                            onClick = { viewModel.onShowArchivedChange(!state.showArchived) },
                            label = { Text("Show archived") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_archive), contentDescription = null) }
                        )
                    }
                }
            }
            when {
                state.activeCount == 0 && !state.showArchived -> item {
                    CoffeeCard(Modifier.fillMaxWidth()) {
                        EmptyState(
                            icon = R.drawable.ic_nav_inventory,
                            title = "No ingredients yet",
                            message = "Add what you buy and use, like coffee beans, milk and cups. " +
                                "Then set up recipes so each sale can take stock out automatically."
                        )
                    }
                }
                state.ingredients.isEmpty() -> item {
                    CoffeeCard(Modifier.fillMaxWidth()) {
                        EmptyState(
                            icon = R.drawable.ic_search,
                            title = if (state.needsRestockOnly && state.query.isBlank()) "Nothing needs restocking"
                            else "No matching ingredients",
                            message = if (state.needsRestockOnly && state.query.isBlank()) "Every ingredient is above its reorder level."
                            else "Try another search or filter."
                        )
                    }
                }
                else -> items(state.ingredients, key = { it.id }) { ingredient ->
                    IngredientRow(ingredient, onClick = { onOpenIngredient(ingredient.id) })
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(ingredient: IngredientEntity, onClick: () -> Unit) {
    CoffeeCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(ingredient.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        ingredient.category.ifBlank { null },
                        formatUnitCost(ingredient.averageCostCentavosPerUnit, ingredient.baseUnit)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IngredientStatusChip(ingredient)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatQuantity(ingredient.quantityOnHandMilli, ingredient.baseUnit),
                    style = MaterialTheme.typography.titleMedium
                )
                Text("on hand", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
