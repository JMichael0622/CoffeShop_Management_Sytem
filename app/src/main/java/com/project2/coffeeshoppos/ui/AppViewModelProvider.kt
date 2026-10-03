package com.project2.coffeeshoppos.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.project2.coffeeshoppos.AppContainer
import com.project2.coffeeshoppos.CoffeeShopApplication
import com.project2.coffeeshoppos.ui.expenses.ExpenseEditViewModel
import com.project2.coffeeshoppos.ui.expenses.ExpensesViewModel
import com.project2.coffeeshoppos.ui.expenses.RecurringExpensesViewModel
import com.project2.coffeeshoppos.ui.inventory.IngredientDetailViewModel
import com.project2.coffeeshoppos.ui.inventory.IngredientEditViewModel
import com.project2.coffeeshoppos.ui.inventory.InventoryViewModel
import com.project2.coffeeshoppos.ui.inventory.RecipeEditViewModel
import com.project2.coffeeshoppos.ui.inventory.RecipesViewModel
import com.project2.coffeeshoppos.ui.manage.ManageViewModel
import com.project2.coffeeshoppos.ui.menu.AddOnsViewModel
import com.project2.coffeeshoppos.ui.menu.CategoriesViewModel
import com.project2.coffeeshoppos.ui.menu.MenuViewModel
import com.project2.coffeeshoppos.ui.menu.ProductEditViewModel
import com.project2.coffeeshoppos.ui.orders.OrderDetailViewModel
import com.project2.coffeeshoppos.ui.orders.OrdersViewModel
import com.project2.coffeeshoppos.ui.pos.PosViewModel

/**
 * Tells Android how to build each ViewModel with the repositories it needs.
 * Usage in a screen: `viewModel<ManageViewModel>(factory = AppViewModelProvider.Factory)`.
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val container = appContainer()
            ManageViewModel(
                catalogRepository = container.catalogRepository,
                inventoryRepository = container.inventoryRepository,
                orderRepository = container.orderRepository,
                expenseRepository = container.expenseRepository
            )
        }
        initializer { MenuViewModel(appContainer().catalogRepository) }
        // SavedStateHandle carries the route arguments, e.g. which product to edit.
        initializer { ProductEditViewModel(createSavedStateHandle(), appContainer().catalogRepository) }
        initializer { CategoriesViewModel(appContainer().catalogRepository) }
        initializer { AddOnsViewModel(appContainer().catalogRepository) }
        initializer { InventoryViewModel(appContainer().inventoryRepository) }
        initializer { IngredientEditViewModel(createSavedStateHandle(), appContainer().inventoryRepository) }
        initializer {
            val container = appContainer()
            IngredientDetailViewModel(createSavedStateHandle(), container.inventoryRepository, container.clock)
        }
        initializer { RecipesViewModel(appContainer().inventoryRepository) }
        initializer { RecipeEditViewModel(createSavedStateHandle(), appContainer().inventoryRepository) }
        initializer {
            val container = appContainer()
            PosViewModel(container.catalogRepository, container.orderRepository)
        }
        initializer {
            val container = appContainer()
            OrdersViewModel(container.orderRepository, container.clock)
        }
        initializer { OrderDetailViewModel(createSavedStateHandle(), appContainer().orderRepository) }
        initializer {
            val container = appContainer()
            ExpensesViewModel(container.expenseRepository, container.clock)
        }
        initializer {
            val container = appContainer()
            ExpenseEditViewModel(createSavedStateHandle(), container.expenseRepository, container.clock)
        }
        initializer { RecurringExpensesViewModel(appContainer().expenseRepository) }
    }
}

private fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CoffeeShopApplication).container
