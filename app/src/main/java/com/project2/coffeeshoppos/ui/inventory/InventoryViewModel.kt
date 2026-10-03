package com.project2.coffeeshoppos.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.domain.StockLevel
import com.project2.coffeeshoppos.domain.stockLevel
import com.project2.coffeeshoppos.domain.stockValueCentavos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class InventoryUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val needsRestockOnly: Boolean = false,
    val showArchived: Boolean = false,
    val ingredients: List<IngredientEntity> = emptyList(),
    val activeCount: Int = 0,
    val stockValueCentavos: Long = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
)

private data class InventoryFilters(val query: String, val needsRestockOnly: Boolean, val showArchived: Boolean)

class InventoryViewModel(repository: InventoryRepository) : ViewModel() {
    private val filters = MutableStateFlow(InventoryFilters("", needsRestockOnly = false, showArchived = false))

    val uiState: StateFlow<InventoryUiState> = combine(repository.observeAllIngredients(), filters) { all, filters ->
        val active = all.filterNot { it.isArchived }
        val query = filters.query.trim()
        val visible = all
            .filter { filters.showArchived || !it.isArchived }
            .filter { !filters.needsRestockOnly || (!it.isArchived && it.stockLevel != StockLevel.OK) }
            .filter { query.isEmpty() || it.name.contains(query, true) || it.category.contains(query, true) }
            // Out of stock first, then low, then the rest; archived last.
            .sortedWith(compareBy({ it.isArchived }, { it.stockLevel != StockLevel.OUT }, { it.stockLevel != StockLevel.LOW }))
        InventoryUiState(
            isLoading = false,
            query = filters.query,
            needsRestockOnly = filters.needsRestockOnly,
            showArchived = filters.showArchived,
            ingredients = visible,
            activeCount = active.size,
            stockValueCentavos = active.sumOf { it.stockValueCentavos },
            lowStockCount = active.count { it.stockLevel == StockLevel.LOW },
            outOfStockCount = active.count { it.stockLevel == StockLevel.OUT }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    fun onQueryChange(query: String) = filters.update { it.copy(query = query) }
    fun onNeedsRestockOnlyChange(value: Boolean) = filters.update { it.copy(needsRestockOnly = value) }
    fun onShowArchivedChange(value: Boolean) = filters.update { it.copy(showArchived = value) }
}
