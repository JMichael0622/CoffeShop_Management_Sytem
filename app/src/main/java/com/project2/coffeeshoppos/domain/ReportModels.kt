package com.project2.coffeeshoppos.domain

import com.project2.coffeeshoppos.data.local.entity.ExpenseEntity
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import com.project2.coffeeshoppos.data.local.entity.RefundEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Whole days from [start] to [endInclusive], in the shop's time zone. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "Range ends before it starts" }
    }

    val dayCount: Int get() = ChronoUnit.DAYS.between(start, endInclusive).toInt() + 1
    val isSingleDay: Boolean get() = start == endInclusive
    val days: List<LocalDate> get() = (0 until dayCount).map { start.plusDays(it.toLong()) }

    companion object {
        fun day(date: LocalDate) = DateRange(date, date)
    }
}

/** Ready-made report periods. Weeks start on Monday. */
enum class ReportPeriod(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    CUSTOM("Custom");

    /** The range for this period as of [today]. CUSTOM has no fixed range and returns today. */
    fun range(today: LocalDate): DateRange = when (this) {
        TODAY, CUSTOM -> DateRange.day(today)
        YESTERDAY -> DateRange.day(today.minusDays(1))
        THIS_WEEK -> DateRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today)
        THIS_MONTH -> DateRange(today.withDayOfMonth(1), today)
        LAST_MONTH -> today.minusMonths(1).let { DateRange(it.withDayOfMonth(1), it.withDayOfMonth(it.lengthOfMonth())) }
    }
}

/**
 * The profit figures for a period. All money is whole centavos.
 *
 * - Gross sales: item totals of orders completed in the period, before discounts.
 * - Net sales: gross − discounts − refunds given in the period. Cancelled orders never count.
 * - Cost of goods: ingredient cost fixed at completion, minus stock returned by refunds.
 * - Stock losses: wastage plus stock-count adjustments at average cost (extra stock found lowers it).
 * - Net profit: net sales − cost of goods − stock losses − operating expenses.
 */
data class ProfitSummary(
    val grossSalesCentavos: Long,
    val discountsCentavos: Long,
    val refundsCentavos: Long,
    val netSalesCentavos: Long,
    val cogsCentavos: Long,
    val stockLossCentavos: Long,
    val operatingExpensesCentavos: Long,
    /** Orders completed in the period, including ones refunded since. */
    val orderCount: Int,
) {
    val grossProfitCentavos: Long get() = netSalesCentavos - cogsCentavos
    val netProfitCentavos: Long get() = grossProfitCentavos - stockLossCentavos - operatingExpensesCentavos
    val averageOrderCentavos: Long? get() = if (orderCount == 0) null else (grossSalesCentavos - discountsCentavos) / orderCount
}

/** One day in a multi-day report. */
data class DayTotals(
    val date: LocalDate,
    val orderCount: Int,
    val netSalesCentavos: Long,
    val expensesCentavos: Long,
)

object ReportMath {
    /**
     * Builds the profit summary. [orders] are those completed in the period (completed or
     * refunded since); [saleRefunds] are refunds of completed orders given in the period;
     * [movementCosts] are ledger totals by type for the period.
     */
    fun summarize(
        orders: List<OrderEntity>,
        saleRefunds: List<RefundEntity>,
        movementCosts: Map<MovementType, Long>,
        expenses: List<ExpenseEntity>,
    ): ProfitSummary {
        val gross = orders.sumOf { it.subtotalCentavos }
        val discounts = orders.sumOf { it.discountCentavos }
        val refunds = saleRefunds.sumOf { it.amountCentavos }
        // Ledger costs are negative for stock leaving and positive for stock coming back.
        val returnedToStock = movementCosts[MovementType.REFUND_RETURN] ?: 0
        val stockLoss = -((movementCosts[MovementType.WASTAGE] ?: 0) + (movementCosts[MovementType.ADJUSTMENT] ?: 0))
        return ProfitSummary(
            grossSalesCentavos = gross,
            discountsCentavos = discounts,
            refundsCentavos = refunds,
            netSalesCentavos = gross - discounts - refunds,
            cogsCentavos = orders.sumOf { it.cogsCentavos ?: 0 } - returnedToStock,
            stockLossCentavos = stockLoss,
            operatingExpensesCentavos = expenses.sumOf { it.amountCentavos },
            orderCount = orders.size
        )
    }

    /** Orders, net sales and expenses for every day in [range], including quiet days. */
    fun dailyTotals(
        range: DateRange,
        orders: List<OrderEntity>,
        saleRefunds: List<RefundEntity>,
        expenses: List<ExpenseEntity>,
        zone: ZoneId,
    ): List<DayTotals> {
        val ordersByDay = orders.groupBy { requireNotNull(it.completedAt).atZone(zone).toLocalDate() }
        val refundsByDay = saleRefunds.groupBy { it.refundedAt.atZone(zone).toLocalDate() }
        val expensesByDay = expenses.groupBy { it.incurredDate }
        return range.days.map { day ->
            val dayOrders = ordersByDay[day].orEmpty()
            DayTotals(
                date = day,
                orderCount = dayOrders.size,
                netSalesCentavos = dayOrders.sumOf { it.totalCentavos } - refundsByDay[day].orEmpty().sumOf { it.amountCentavos },
                expensesCentavos = expensesByDay[day].orEmpty().sumOf { it.amountCentavos }
            )
        }
    }
}
