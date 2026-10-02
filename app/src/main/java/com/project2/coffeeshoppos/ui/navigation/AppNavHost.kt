package com.project2.coffeeshoppos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.project2.coffeeshoppos.ui.ComingSoonScreen
import com.project2.coffeeshoppos.ui.dashboard.DashboardScreen
import com.project2.coffeeshoppos.ui.manage.ManageScreen
import com.project2.coffeeshoppos.ui.menu.AddOnsScreen
import com.project2.coffeeshoppos.ui.menu.CategoriesScreen
import com.project2.coffeeshoppos.ui.menu.MenuScreen
import com.project2.coffeeshoppos.ui.menu.ProductEditScreen

/** The map of every screen in the app. New screens are added here as each phase is built. */
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = DashboardRoute,
        modifier = modifier
    ) {
        composable<DashboardRoute> {
            DashboardScreen(
                onNewOrder = { navController.navigateToTab(TopLevelDestination.Pos) },
                onAddStock = { navController.navigateToTab(TopLevelDestination.Inventory) },
                onRecordExpense = { navController.navigateToTab(TopLevelDestination.Expenses) },
                onOpenManage = { navController.navigate(ManageRoute) }
            )
        }
        composable<PosRoute> { ComingSoonScreen(TopLevelDestination.Pos) }
        composable<InventoryRoute> { ComingSoonScreen(TopLevelDestination.Inventory) }
        composable<ExpensesRoute> { ComingSoonScreen(TopLevelDestination.Expenses) }
        composable<ReportsRoute> { ComingSoonScreen(TopLevelDestination.Reports) }
        composable<ManageRoute> {
            ManageScreen(
                onBack = { navController.popBackStack() },
                onOpenMenu = { navController.navigate(MenuRoute) },
                onOpenAddOns = { navController.navigate(AddOnsRoute) }
            )
        }
        composable<MenuRoute> {
            MenuScreen(
                onBack = { navController.popBackStack() },
                onAddProduct = { navController.navigate(ProductEditRoute()) },
                onEditProduct = { id -> navController.navigate(ProductEditRoute(productId = id)) },
                onOpenCategories = { navController.navigate(CategoriesRoute) },
                onOpenAddOns = { navController.navigate(AddOnsRoute) }
            )
        }
        composable<ProductEditRoute> {
            ProductEditScreen(onClose = { navController.popBackStack() })
        }
        composable<CategoriesRoute> { CategoriesScreen(onBack = { navController.popBackStack() }) }
        composable<AddOnsRoute> { AddOnsScreen(onBack = { navController.popBackStack() }) }
    }
}

/**
 * Switches tabs the standard Android way: each tab keeps its own scroll position, and pressing
 * Back from any tab returns to the dashboard instead of stepping through every tab visited.
 */
fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
