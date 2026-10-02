package com.project2.coffeeshoppos.ui.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.data.repository.ExpenseRepository
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.data.repository.OrderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface DatabaseStatus {
    data object Loading : DatabaseStatus
    data class Ready(
        val schemaVersion: Int,
        val products: Int,
        val ingredients: Int,
        val orders: Int,
        val expenses: Int,
    ) : DatabaseStatus
    data class Error(val message: String) : DatabaseStatus
}

class ManageViewModel(
    catalogRepository: CatalogRepository,
    inventoryRepository: InventoryRepository,
    orderRepository: OrderRepository,
    expenseRepository: ExpenseRepository,
) : ViewModel() {

    /** Live record counts. They update automatically whenever the database changes. */
    val databaseStatus: StateFlow<DatabaseStatus> = combine(
        catalogRepository.observeProductCount(),
        inventoryRepository.observeIngredientCount(),
        orderRepository.observeOrderCount(),
        expenseRepository.observeExpenseCount(),
    ) { products, ingredients, orders, expenses ->
        DatabaseStatus.Ready(
            schemaVersion = CoffeeShopDatabase.VERSION,
            products = products,
            ingredients = ingredients,
            orders = orders,
            expenses = expenses
        ) as DatabaseStatus
    }
        .catch { emit(DatabaseStatus.Error(it.message ?: "Unknown database error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DatabaseStatus.Loading)
}
