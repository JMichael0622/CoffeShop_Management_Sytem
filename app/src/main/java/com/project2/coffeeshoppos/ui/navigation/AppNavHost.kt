package com.project2.coffeeshoppos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.domain.Permission
import com.project2.coffeeshoppos.domain.RecipeTarget
import com.project2.coffeeshoppos.ui.accounts.AccountsScreen
import com.project2.coffeeshoppos.ui.auth.LocalCurrentUser
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.ui.customers.CustomerDetailScreen
import com.project2.coffeeshoppos.ui.customers.CustomersScreen
import com.project2.coffeeshoppos.ui.customers.LoyaltySettingsScreen
import com.project2.coffeeshoppos.ui.dashboard.DashboardScreen
import com.project2.coffeeshoppos.ui.data.BackupScreen
import com.project2.coffeeshoppos.ui.data.ExportScreen
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
import com.project2.coffeeshoppos.ui.reports.ReportsScreen

/** The map of every screen in the app. New screens are added here as each phase is built. */
@Composable
fun AppNavHost(navController: NavHostController, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val back: () -> Unit = { navController.popBackStack() }
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
                onOpenManage = { navController.navigate(ManageRoute) },
                onOpenOrder = { id -> navController.navigate(OrderDetailRoute(id)) },
                onOpenBackup = { navController.navigate(BackupRoute) },
                onSignOut = onSignOut
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
            Allowed(Permission.EXPENSES, back) {
            ExpensesScreen(
                onAddExpense = { navController.navigate(ExpenseEditRoute()) },
                onOpenExpense = { id -> navController.navigate(ExpenseEditRoute(expenseId = id)) },
                onRecordReminder = { templateId -> navController.navigate(ExpenseEditRoute(templateId = templateId)) },
                onOpenRecurring = { navController.navigate(RecurringExpensesRoute) }
            )
            }
        }
        composable<ReportsRoute> { Allowed(Permission.REPORTS, back) { ReportsScreen() } }
        composable<ManageRoute> {
            ManageScreen(
                onBack = { navController.popBackStack() },
                onOpenMenu = { navController.navigate(MenuRoute) },
                onOpenAddOns = { navController.navigate(AddOnsRoute) },
                onOpenRecipes = { navController.navigate(RecipesRoute) },
                onOpenCustomers = { navController.navigate(CustomersRoute) },
                onOpenLoyaltySettings = { navController.navigate(LoyaltySettingsRoute) },
                onOpenAccounts = { navController.navigate(AccountsRoute) },
                onOpenExport = { navController.navigate(ExportRoute) },
                onOpenBackup = { navController.navigate(BackupRoute) },
                onSignOut = onSignOut
            )
        }
        composable<MenuRoute> {
            Allowed(Permission.MENU, back) {
            MenuScreen(
                onBack = { navController.popBackStack() },
                onAddProduct = { navController.navigate(ProductEditRoute()) },
                onEditProduct = { id -> navController.navigate(ProductEditRoute(productId = id)) },
                onOpenCategories = { navController.navigate(CategoriesRoute) },
                onOpenAddOns = { navController.navigate(AddOnsRoute) }
            )
            }
        }
        composable<ProductEditRoute> {
            Allowed(Permission.MENU, back) {
            ProductEditScreen(
                onClose = { navController.popBackStack() },
                onEditRecipe = { variationId -> openRecipe(RecipeTarget.Variation(variationId)) }
            )
            }
        }
        composable<CategoriesRoute> { Allowed(Permission.MENU, back) { CategoriesScreen(onBack = back) } }
        composable<AddOnsRoute> {
            Allowed(Permission.MENU, back) {
            AddOnsScreen(
                onBack = { navController.popBackStack() },
                onEditRecipe = { addOnId -> openRecipe(RecipeTarget.AddOn(addOnId)) }
            )
            }
        }
        composable<IngredientEditRoute> {
            IngredientEditScreen(
                onClose = { navController.popBackStack() },
                // Replace the ingredient page underneath with one that opens the correction dialog.
                onCorrectCost = { id ->
                    navController.navigate(IngredientDetailRoute(id, openCostCorrection = true)) {
                        popUpTo<IngredientDetailRoute> { inclusive = true }
                    }
                }
            )
        }
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
        composable<OrderDetailRoute> {
            OrderDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenCustomer = { id -> navController.navigate(CustomerDetailRoute(id)) }
            )
        }
        composable<ExpenseEditRoute> { Allowed(Permission.EXPENSES, back) { ExpenseEditScreen(onClose = back) } }
        composable<RecurringExpensesRoute> { Allowed(Permission.EXPENSES, back) { RecurringExpensesScreen(onBack = back) } }
        composable<ExportRoute> { ExportScreen(onBack = { navController.popBackStack() }) }
        composable<BackupRoute> { BackupScreen(onBack = { navController.popBackStack() }) }
        composable<CustomersRoute> {
            CustomersScreen(
                onBack = { navController.popBackStack() },
                onOpenCustomer = { id -> navController.navigate(CustomerDetailRoute(id)) },
                onOpenSettings = { navController.navigate(LoyaltySettingsRoute) }
            )
        }
        composable<CustomerDetailRoute> {
            CustomerDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenOrder = { id -> navController.navigate(OrderDetailRoute(id)) }
            )
        }
        composable<LoyaltySettingsRoute> { Allowed(Permission.LOYALTY_SETTINGS, back) { LoyaltySettingsScreen(onBack = back) } }
        composable<AccountsRoute> { Allowed(Permission.ACCOUNTS, back) { AccountsScreen(onBack = back) } }
    }
}

/**
 * Switches tabs the standard Android way: each tab keeps its own scroll position, and pressing
 * Back from any tab returns to the dashboard instead of stepping through every tab visited.
 */
/**
 * Shows [content] only to people with [permission]. The menus already hide what someone can't
 * use; this also stops them reaching it any other way.
 */
@Composable
private fun Allowed(permission: Permission, onBack: () -> Unit, content: @Composable () -> Unit) {
    if (LocalCurrentUser.current.can(permission)) {
        content()
    } else {
        SubScreenScaffold(title = "Admin only", onBack = onBack) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    EmptyState(
                        icon = R.drawable.ic_person,
                        title = "Admin only",
                        message = "Your account can't open this. Ask an admin to sign in."
                    )
                }
            }
        }
    }
}

fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
