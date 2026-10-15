package com.project2.coffeeshoppos.ui.inventory

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.project2.coffeeshoppos.data.repository.RecipeCostRow
import com.project2.coffeeshoppos.domain.RecipeTarget
import com.project2.coffeeshoppos.domain.costPercent
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.InfoBanner
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.StatusChip
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.ui.theme.LocalCoffeeExtraColors
import com.project2.coffeeshoppos.util.formatPeso

/** Every size and add-on with what its ingredients cost. Tap one to edit its recipe. */
@Composable
fun RecipesScreen(
    onBack: () -> Unit,
    onEditRecipe: (RecipeTarget) -> Unit,
    viewModel: RecipesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SubScreenScaffold(title = "Recipes", onBack = onBack) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@SubScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "A recipe lists the ingredients used to make one of each size. When orders are taken, " +
                        "this stock is removed automatically. Costs use today's average ingredient costs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.missingCount > 0) {
                item {
                    InfoBanner(
                        icon = R.drawable.ic_warning,
                        text = "${state.missingCount} " + (if (state.missingCount == 1) "size or add-on has" else "sizes and add-ons have") +
                            " no recipe yet, so selling them uses no stock. Look for \"No recipe\" below."
                    )
                }
            }
            if (state.products.isEmpty()) {
                item {
                    CoffeeCard(Modifier.fillMaxWidth()) {
                        EmptyState(
                            icon = R.drawable.ic_cafe,
                            title = "No products yet",
                            message = "Add products in Manage › Products & categories, then come back to set their recipes."
                        )
                    }
                }
            }
            items(state.products, key = { it.productId }) { product ->
                CoffeeCard(Modifier.fillMaxWidth()) {
                    Column {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                            Text(product.productName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                product.categoryName ?: "No category",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        product.sizes.forEachIndexed { index, size ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            RecipeRow(size, onClick = { onEditRecipe(size.target) })
                        }
                    }
                }
            }
            if (state.addOns.isNotEmpty()) {
                item { SectionHeader("Add-ons") }
                item {
                    CoffeeCard(Modifier.fillMaxWidth()) {
                        Column {
                            state.addOns.forEachIndexed { index, addOn ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                RecipeRow(addOn, onClick = { onEditRecipe(addOn.target) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeRow(row: RecipeCostRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${row.name} · ${formatPeso(row.priceCentavos)}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.lineCount == 0) {
                StatusChip("No recipe", color = LocalCoffeeExtraColors.current.warning)
            } else {
                val percent = costPercent(row.costCentavos, row.priceCentavos)
                Text(
                    listOfNotNull(
                        "${row.lineCount} " + if (row.lineCount == 1) "ingredient" else "ingredients",
                        "cost ${formatPeso(row.costCentavos)}",
                        percent?.let { "$it% of price" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (row.hasUncostedIngredient) {
                    StatusChip("Some costs unknown", color = LocalCoffeeExtraColors.current.warning)
                }
            }
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
