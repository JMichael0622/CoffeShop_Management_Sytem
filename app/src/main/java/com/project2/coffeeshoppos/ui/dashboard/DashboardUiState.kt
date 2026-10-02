package com.project2.coffeeshoppos.ui.dashboard

/**
 * Everything the dashboard displays. Money values are whole centavos.
 *
 * A null figure means "not available yet" and is shown as a dash, never as a fake number.
 * In Phase 7 a ViewModel will fill this from the Room database.
 */
data class DashboardUiState(
    val isDataConnected: Boolean = false,
    val grossSalesCentavos: Long? = null,
    val completedOrders: Int? = null,
    val discountsAndRefundsCentavos: Long? = null,
    val operatingExpensesCentavos: Long? = null,
    val cogsCentavos: Long? = null,
    val estimatedProfitCentavos: Long? = null,
    val lowStockCount: Int? = null,
    val outOfStockCount: Int? = null,
    val stockAlerts: List<StockAlert> = emptyList(),
    val bestSellers: List<BestSeller> = emptyList(),
    val recentOrders: List<RecentOrder> = emptyList(),
)

data class StockAlert(
    val ingredientName: String,
    val quantityLabel: String,
    val isOutOfStock: Boolean,
)

data class BestSeller(
    val productName: String,
    val quantitySold: Int,
    val salesCentavos: Long,
)

data class RecentOrder(
    val orderNumber: String,
    val timeLabel: String,
    val statusLabel: String,
    val totalCentavos: Long,
)
