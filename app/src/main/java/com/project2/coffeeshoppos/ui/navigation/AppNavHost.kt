package com.project2.coffeeshoppos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.project2.coffeeshoppos.domain.RecipeTarget
import com.project2.coffeeshoppos.ui.ComingSoonScreen
import com.project2.coffeeshoppos.ui.dashboard.DashboardScreen
import com.project2.coffeeshoppos.ui.expenses.ExpenseEditScreen
import com.project2.coffeeshoppos.ui.expenses.ExpensesScreen
import com.project2.coffeeshoppos.ui.expenses.RecurringExpensesScreen
import com.project2.coffeeshoppos.ui.inventory.IngredientDetailScreen
import com.project2.coffeeshoppos.ui.inventory.IngredientEditScreen
import com.project2.coffeeshoppos.ui.inventory.InventoryScreen
import com.project2.coffeeshoppos.ui.inventory.RecipeEditScreen
import com.project2.coffeeshoppos.ui.inventory.RecipesScreen
import com.project2.coffeeshoppos.ui.manage.ManageScreen
import com.project2.coffeeshoppos.ui.menu.AddOnsScreen
import com.project2.coffeeshoppos.ui.menu.CategoriesScreen
import com.project2.coffeeshoppos.ui.menu.MenuScreen
import com.project2.coffeeshoppos.ui.menu.ProductEditScreen
import com.project2.coffeeshoppos.ui.orders.OrderDetailScreen
import com.project2.coffeeshoppos.ui.orders.OrdersScreen
import com.project2.coffeeshoppos.ui.pos.PosScreen

/** The map of every screen in the app. New screens are added here as each phase is built. */
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val openRecipe = { target: RecipeTarget ->
        navController.navigate(
            when (target) {
                is RecipeTarget.Variation -> RecipeEditRoute(variationId = target.variationId)
                is RecipeTarget.AddOn -> RecipeEditRoute(addOnId = target.addOnId)
            }
        )
    }
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
        composable<PosRoute> {
            PosScreen(
                onOpenOrders = { navController.navigate(OrdersRoute) },
                onOpenOrder = { id -> navController.navigate(OrderDetailRoute(id)) },
                onOpenMenuSetup = { navController.navigate(MenuRoute) }
            )
        }
        composable<InventoryRoute> {
            InventoryScreen(
                onAddIngredient = { navController.navigate(IngredientEditRoute()) },
                onOpenIngredient = { id -> navController.navigate(IngredientDetailRoute(id)) },
                onOpenRecipes = { navController.navigate(RecipesRoute) }
            )
        }
        composable<ExpensesRoute> {
            ExpensesScreen(
                onAddExpense = { navController.navigate(ExpenseEditRoute()) },
                onOpenExpense = { id -> navController.navigate(ExpenseEditRoute(expenseId = id)) },
                onRecordReminder = { templateId -> navController.navigate(ExpenseEditRoute(templateId = templateId)) },
                onOpenRecurring = { navController.navigate(RecurringExpensesRoute) }
            )
        }
        composable<ReportsRoute> { ComingSoonScreen(TopLevelDestination.Reports) }
        composable<ManageRoute> {
            ManageScreen(
                onBack = { navController.popBackStack() },
                onOpenMenu = { navController.navigate(MenuRoute) },
                onOpenAddOns = { navController.navigate(AddOnsRoute) },
                onOpenRecipes = { navController.navigate(RecipesRoute) }
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
            ProductEditScreen(
                onClose = { navController.popBackStack() },
                onEditRecipe = { variationId -> openRecipe(RecipeTarget.Variation(variationId)) }
            )
        }
        composable<CategoriesRoute> { CategoriesScreen(onBack = { navController.popBackStack() }) }
        composable<AddOnsRoute> {
            AddOnsScreen(
                onBack = { navController.popBackStack() },
                onEditRecipe = { addOnId -> openRecipe(RecipeTarget.AddOn(addOnId)) }
            )
        }
        composable<IngredientEditRoute> { IngredientEditScreen(onClose = { navController.popBackStack() }) }
        composable<IngredientDetailRoute> {
            IngredientDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(IngredientEditRoute(id)) }
            )
        }
        composable<RecipesRoute> {
            RecipesScreen(onBack = { navController.popBackStack() }, onEditRecipe = openRecipe)
        }
        composable<RecipeEditRoute> {
            RecipeEditScreen(
                onClose = { navController.popBackStack() },
                onAddIngredient = { navController.navigate(IngredientEditRoute()) }
            )
        }
        composable<OrdersRoute> {
            OrdersScreen(
                onBack = { navController.popBackStack() },
                onOpenOrder = { id -> navController.navigate(OrderDetailRoute(id)) }
            )
        }
        composable<OrderDetailRoute> { OrderDetailScreen(onBack = { navController.popBackStack() }) }
        composable<ExpenseEditRoute> { ExpenseEditScreen(onClose = { navController.popBackStack() }) }
        composable<RecurringExpensesRoute> { RecurringExpensesScreen(onBack = { navController.popBackStack() }) }
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
