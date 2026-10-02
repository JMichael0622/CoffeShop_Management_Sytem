package com.project2.coffeeshoppos.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.domain.ProductSummary
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.StatusChip
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.ui.theme.LocalCoffeeExtraColors
import com.project2.coffeeshoppos.util.formatPeso

@Composable
fun MenuScreen(
    onBack: () -> Unit,
    onAddProduct: () -> Unit,
    onEditProduct: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAddOns: () -> Unit,
    viewModel: MenuViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)

    SubScreenScaffold(
        title = "Menu",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = onOpenCategories) {
                Icon(painterResource(R.drawable.ic_category), contentDescription = "Categories")
            }
            IconButton(onClick = onOpenAddOns) {
                Icon(painterResource(R.drawable.ic_discount), contentDescription = "Add-ons")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProduct,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Add product") },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@SubScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            // Extra bottom space so the floating button never covers the last product.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search products") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
            item { FilterRow(state, viewModel) }

            when {
                state.totalProducts == 0 && !state.showArchived -> item {
                    CoffeeCard(Modifier.fillMaxWidth()) {
                        EmptyState(
                            icon = R.drawable.ic_cafe,
                            title = "No products yet",
                            message = "Tip: create categories first (e.g. Coffee, Non-coffee, Pastries) " +
                                "with the shapes icon at the top, then tap \"Add product\"."
                        )
                    }
                }
                state.products.isEmpty() -> item {
                    CoffeeCard(Modifier.fillMaxWidth()) {
                        EmptyState(
                            icon = R.drawable.ic_search,
                            title = "No matching products",
                            message = "Try another search or category."
                        )
                    }
                }
                else -> items(state.products, key = { it.id }) { product ->
                    ProductRow(
                        product = product,
                        onClick = { onEditProduct(product.id) },
                        onAvailableChange = { viewModel.setAvailable(product, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: MenuUiState, viewModel: MenuViewModel) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = state.filter == CategoryFilter.All,
                onClick = { viewModel.onFilterChange(CategoryFilter.All) },
                label = { Text("All") }
            )
        }
        items(state.categories, key = { it.id }) { category ->
            FilterChip(
                selected = state.filter == CategoryFilter.Category(category.id),
                onClick = { viewModel.onFilterChange(CategoryFilter.Category(category.id)) },
                label = { Text(category.name) }
            )
        }
        if (state.hasUncategorized) {
            item {
                FilterChip(
                    selected = state.filter == CategoryFilter.Uncategorized,
                    onClick = { viewModel.onFilterChange(CategoryFilter.Uncategorized) },
                    label = { Text("No category") }
                )
            }
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

@Composable
private fun ProductRow(
    product: ProductSummary,
    onClick: () -> Unit,
    onAvailableChange: (Boolean) -> Unit,
) {
    CoffeeCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        product.categoryName ?: "No category",
                        priceLabel(product),
                        if (product.sizeCount > 1) "${product.sizeCount} sizes" else null
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    product.isArchived -> StatusChip("Archived")
                    !product.isAvailable -> StatusChip("Sold out", color = LocalCoffeeExtraColors.current.warning)
                }
            }
            if (!product.isArchived) {
                Switch(
                    checked = product.isAvailable,
                    onCheckedChange = onAvailableChange,
                    modifier = Modifier.semantics {
                        contentDescription = "${product.name} available"
                    }
                )
            }
        }
    }
}

private fun priceLabel(product: ProductSummary): String {
    val min = product.minPriceCentavos ?: return "No price"
    val max = product.maxPriceCentavos ?: min
    return if (min == max) formatPeso(min) else "${formatPeso(min)} – ${formatPeso(max)}"
}
