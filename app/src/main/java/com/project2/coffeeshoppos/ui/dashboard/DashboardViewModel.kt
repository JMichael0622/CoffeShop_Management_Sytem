package com.project2.coffeeshoppos.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.data.repository.LoyaltyRepository
import com.project2.coffeeshoppos.data.repository.OrderRepository
import com.project2.coffeeshoppos.data.repository.ReportRepository
import com.project2.coffeeshoppos.data.repository.shortOrderNumber
import com.project2.coffeeshoppos.domain.DateRange
import com.project2.coffeeshoppos.domain.StockLevel
import com.project2.coffeeshoppos.domain.formatQuantity
import com.project2.coffeeshoppos.domain.stockLevel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** Today's figures for the dashboard, from the same numbers as the Reports tab. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val reportRepository: ReportRepository,
    inventoryRepository: InventoryRepository,
    orderRepository: OrderRepository,
    loyaltyRepository: LoyaltyRepository,
) : ViewModel() {
    /** Re-checks the date every minute, so a dashboard left open overnight rolls over to the new day. */
    private val today = flow {
        while (true) {
            emit(reportRepository.today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<DashboardUiState> = combine(
        today.flatMapLatest { reportRepository.observeReport(DateRange.day(it)) },
        inventoryRepository.observeIngredients(),
        orderRepository.observeRecentOrders(limit = RECENT_ORDERS),
        today.flatMapLatest { loyaltyRepository.observeCustomersServedOn(it) },
    ) { report, ingredients, recent, loyaltyCustomers ->
        val summary = report.summary
        val zone = ZoneId.systemDefault()
        val needingStock = ingredients
            .filter { it.stockLevel != StockLevel.OK }
            .sortedWith(compareBy({ it.stockLevel != StockLevel.OUT }, { it.name.lowercase() }))
        DashboardUiState(
            grossSalesCentavos = summary.grossSalesCentavos,
            completedOrders = summary.orderCount,
            loyaltyCustomers = loyaltyCustomers,
            loyaltyRewardsCentavos = summary.loyaltyRewardsCentavos,
            discountsAndRefundsCentavos = summary.discountsCentavos + summary.loyaltyRewardsCentavos + summary.refundsCentavos,
            operatingExpensesCentavos = summary.operatingExpensesCentavos,
            cogsCentavos = summary.cogsCentavos,
            estimatedProfitCentavos = summary.netProfitCentavos,
            lowStockCount = ingredients.count { it.stockLevel == StockLevel.LOW },
            outOfStockCount = ingredients.count { it.stockLevel == StockLevel.OUT },
            stockAlerts = needingStock.take(STOCK_ALERTS).map {
                StockAlert(
                    ingredientName = it.name,
                    quantityLabel = formatQuantity(it.quantityOnHandMilli, it.baseUnit),
                    isOutOfStock = it.stockLevel == StockLevel.OUT
                )
            },
            bestSellers = report.productSales.take(BEST_SELLERS).map {
                BestSeller(productName = it.productName, quantitySold = it.quantity, salesCentavos = it.salesCentavos)
            },
            recentOrders = recent.map {
                RecentOrder(
                    orderId = it.id,
                    orderNumber = shortOrderNumber(it.orderNumber),
                    timeLabel = it.createdAt.atZone(zone).format(TIME),
                    statusLabel = it.status.label,
                    totalCentavos = it.totalCentavos
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private companion object {
        const val RECENT_ORDERS = 5
        const val STOCK_ALERTS = 5
        const val BEST_SELLERS = 5
    }
}
