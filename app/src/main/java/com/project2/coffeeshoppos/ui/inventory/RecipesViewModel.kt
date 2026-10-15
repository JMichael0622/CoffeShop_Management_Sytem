package com.project2.coffeeshoppos.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.data.repository.ProductRecipes
import com.project2.coffeeshoppos.data.repository.RecipeCostRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RecipesUiState(
    val isLoading: Boolean = true,
    val products: List<ProductRecipes> = emptyList(),
    val addOns: List<RecipeCostRow> = emptyList(),
    /** Sizes and add-ons with no ingredients yet; their sales will not take stock out. */
    val missingCount: Int = 0,
)

class RecipesViewModel(repository: InventoryRepository) : ViewModel() {
    val uiState: StateFlow<RecipesUiState> = repository.observeRecipeOverview().map { overview ->
        RecipesUiState(
            isLoading = false,
            products = overview.products,
            addOns = overview.addOns,
            missingCount = overview.products.sumOf { product -> product.sizes.count { it.lineCount == 0 } } +
                overview.addOns.count { it.lineCount == 0 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesUiState())
}
