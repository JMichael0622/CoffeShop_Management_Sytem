package com.project2.coffeeshoppos.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.project2.coffeeshoppos.AppContainer
import com.project2.coffeeshoppos.CoffeeShopApplication
import com.project2.coffeeshoppos.ui.manage.ManageViewModel
import com.project2.coffeeshoppos.ui.menu.AddOnsViewModel
import com.project2.coffeeshoppos.ui.menu.CategoriesViewModel
import com.project2.coffeeshoppos.ui.menu.MenuViewModel
import com.project2.coffeeshoppos.ui.menu.ProductEditViewModel

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
    }
}

private fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CoffeeShopApplication).container
