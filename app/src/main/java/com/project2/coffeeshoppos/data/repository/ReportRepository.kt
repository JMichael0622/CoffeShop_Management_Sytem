package com.project2.coffeeshoppos.data.repository

import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.local.dao.MethodTotal
import com.project2.coffeeshoppos.data.local.dao.ProductSales
import com.project2.coffeeshoppos.domain.DateRange
import com.project2.coffeeshoppos.domain.DayTotals
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.MovementType
import com.project2.coffeeshoppos.domain.OrderStatus
import com.project2.coffeeshoppos.domain.ProfitSummary
import com.project2.coffeeshoppos.domain.ReportMath
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Everything the Reports tab and dashboard show for one period. Updates live as data changes. */
data class SalesReport(
    val range: DateRange,
    val summary: ProfitSummary,
    val cancelledCount: Int,
    val refundedCount: Int,
    val salesByMethod: List<MethodTotal>,
    val productSales: List<ProductSales>,
    val expensesByCategory: List<Pair<ExpenseCategory, Long>>,
    /** Cash spent on stock in the period. Not subtracted from profit; cost of goods covers stock as it is used. */
    val purchasesCentavos: Long,
    val wastageCentavos: Long,
    val days: List<DayTotals>,
)

class ReportRepository(private val db: CoffeeShopDatabase, private val clock: Clock) {
    private val dao = db.reportDao()
    private val expenseDao = db.expenseDao()

    fun observeReport(range: DateRange): Flow<SalesReport> {
        val from = range.start.atStartOfDay(clock.zone).toInstant()
        val to = range.endInclusive.plusDays(1).atStartOfDay(clock.zone).toInstant()
        return combine(
            combine(dao.observeCompletedOrders(from, to), dao.observeSaleRefunds(from, to), dao.observeCancelledCount(from, to), ::Triple),
            combine(dao.observeSalesByMethod(from, to), dao.observeProductSales(from, to), ::Pair),
            dao.observeMovementTotals(from, to),
            dao.observePurchasesTotal(from, to),
            expenseDao.observeExpenses(range.start, range.endInclusive),
        ) { (orders, refunds, cancelled), (byMethod, products), movements, purchases, expenses ->
            val movementCosts = movements.associate { it.type to it.costCentavos }
            SalesReport(
                range = range,
                summary = ReportMath.summarize(orders, refunds, movementCosts, expenses),
                cancelledCount = cancelled,
                refundedCount = orders.count { it.status == OrderStatus.REFUNDED },
                salesByMethod = byMethod,
                productSales = products,
                expensesByCategory = expenses.groupBy { it.category }
                    .map { (category, items) -> category to items.sumOf { it.amountCentavos } }
                    .sortedByDescending { it.second },
                purchasesCentavos = purchases,
                wastageCentavos = -(movementCosts[MovementType.WASTAGE] ?: 0),
                days = ReportMath.dailyTotals(range, orders, refunds, expenses, clock.zone)
            )
        }
    }

    /** Today's date in the shop's time zone. */
    fun today(): LocalDate = LocalDate.now(clock)
}
