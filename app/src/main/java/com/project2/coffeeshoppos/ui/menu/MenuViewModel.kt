package com.project2.coffeeshoppos.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.entity.CategoryEntity
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.domain.ProductSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which products the list shows. */
sealed interface CategoryFilter {
    data object All : CategoryFilter
    data object Uncategorized : CategoryFilter
    data class Category(val id: Long) : CategoryFilter
}

data class MenuUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: CategoryFilter = CategoryFilter.All,
    val showArchived: Boolean = false,
    val categories: List<CategoryEntity> = emptyList(),
    val hasUncategorized: Boolean = false,
    val products: List<ProductSummary> = emptyList(),
    val totalProducts: Int = 0,
    val message: String? = null,
)

private data class Filters(val query: String, val filter: CategoryFilter, val showArchived: Boolean)

class MenuViewModel(private val repository: CatalogRepository) : ViewModel() {
    private val filters = MutableStateFlow(Filters("", CategoryFilter.All, showArchived = false))
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MenuUiState> = combine(
        repository.observeProductSummaries(),
        repository.observeCategories(),
        filters,
        message,
    ) { products, categories, filters, message ->
        val categoryOrder = categories.withIndex().associate { (index, category) -> category.id to index }
        val query = filters.query.trim()
        val visible = products
            .filter { filters.showArchived || !it.isArchived }
            .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
            .filter {
                when (val filter = filters.filter) {
                    CategoryFilter.All -> true
                    CategoryFilter.Uncategorized -> it.categoryId == null
                    is CategoryFilter.Category -> it.categoryId == filter.id
                }
            }
            .sortedWith(
                compareBy<ProductSummary>({ it.isArchived }, { categoryOrder[it.categoryId] ?: Int.MAX_VALUE })
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
        MenuUiState(
            isLoading = false,
            query = filters.query,
            filter = filters.filter,
            showArchived = filters.showArchived,
            categories = categories,
            hasUncategorized = products.any { !it.isArchived && it.categoryId == null },
            products = visible,
            totalProducts = products.count { !it.isArchived },
            message = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuUiState())

    fun onQueryChange(query: String) = filters.update { it.copy(query = query) }
    fun onFilterChange(filter: CategoryFilter) = filters.update { it.copy(filter = filter) }
    fun onShowArchivedChange(show: Boolean) = filters.update { it.copy(showArchived = show) }
    fun onMessageShown() = message.update { null }

    fun setAvailable(product: ProductSummary, available: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setProductAvailable(product.id, available) }
                .onSuccess { message.value = "${product.name} is now ${if (available) "available" else "sold out"}" }
                .onFailure { message.value = it.message ?: "Could not update ${product.name}" }
        }
    }
}
