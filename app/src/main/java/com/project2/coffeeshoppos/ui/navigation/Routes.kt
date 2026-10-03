package com.project2.coffeeshoppos.ui.navigation

import androidx.annotation.DrawableRes
import com.project2.coffeeshoppos.R
import kotlinx.serialization.Serializable

// Every screen has a route. Routes are Kotlin objects/classes instead of strings, so a typo is
// a build error rather than a crash. Screens that need an argument (e.g. an order id) will use
// a data class, such as: @Serializable data class OrderDetailRoute(val orderId: Long)

@Serializable object DashboardRoute
@Serializable object PosRoute
@Serializable object InventoryRoute
@Serializable object ExpensesRoute
@Serializable object ReportsRoute

/** Shop management hub: products, categories, backup, security. Opened from the dashboard. */
@Serializable object ManageRoute

// Menu setup (Phase 3)
@Serializable object MenuRoute
@Serializable object CategoriesRoute
@Serializable object AddOnsRoute

/** Product editor. productId = 0 creates a new product. */
@Serializable data class ProductEditRoute(val productId: Long = 0L)

// Inventory (Phase 4)
/** Ingredient editor. ingredientId = 0 creates a new ingredient. */
@Serializable data class IngredientEditRoute(val ingredientId: Long = 0L)
@Serializable data class IngredientDetailRoute(val ingredientId: Long)
@Serializable object RecipesRoute

/** Recipe editor for exactly one of: a product size (variationId) or an add-on (addOnId). */
@Serializable data class RecipeEditRoute(val variationId: Long = 0L, val addOnId: Long = 0L)

// Orders (Phase 5)
@Serializable object OrdersRoute
@Serializable data class OrderDetailRoute(val orderId: Long)

/** The five tabs in the bottom navigation bar. */
enum class TopLevelDestination(
    val route: Any,
    val label: String,
    @param:DrawableRes val icon: Int,
    val comingSoonMessage: String,
) {
    Dashboard(
        route = DashboardRoute,
        label = "Dashboard",
        icon = R.drawable.ic_nav_dashboard,
        comingSoonMessage = ""
    ),
    Pos(
        route = PosRoute,
        label = "POS",
        icon = R.drawable.ic_nav_pos,
        comingSoonMessage = ""
    ),
    Inventory(
        route = InventoryRoute,
        label = "Inventory",
        icon = R.drawable.ic_nav_inventory,
        comingSoonMessage = ""
    ),
    Expenses(
        route = ExpensesRoute,
        label = "Expenses",
        icon = R.drawable.ic_nav_expenses,
        comingSoonMessage = "Recording and reviewing operating expenses arrives in Phase 6."
    ),
    Reports(
        route = ReportsRoute,
        label = "Reports",
        icon = R.drawable.ic_nav_reports,
        comingSoonMessage = "Sales, COGS, expense, and profit reports arrive in Phase 7."
    ),
}
