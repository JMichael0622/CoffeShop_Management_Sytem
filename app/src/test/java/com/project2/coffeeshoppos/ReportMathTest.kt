package com.project2.coffeeshoppos

import com.project2.coffeeshoppos.data.local.entity.ExpenseEntity
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import com.project2.coffeeshoppos.data.local.entity.RefundEntity
import com.project2.coffeeshoppos.domain.DateRange
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.MovementType
import com.project2.coffeeshoppos.domain.OrderStatus
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.PaymentStatus
import com.project2.coffeeshoppos.domain.ReportMath
import com.project2.coffeeshoppos.domain.ReportPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportMathTest {
    private val zone = ZoneId.of("Asia/Manila")

    private fun order(subtotal: Long, discount: Long = 0, cogs: Long, completed: String, status: OrderStatus = OrderStatus.COMPLETED) =
        OrderEntity(
            orderNumber = "x", status = status, subtotalCentavos = subtotal, discountCentavos = discount,
            totalCentavos = subtotal - discount, cogsCentavos = cogs, createdAt = Instant.parse(completed),
            updatedAt = Instant.parse(completed), completedAt = Instant.parse(completed)
        )

    private fun expense(amount: Long, date: LocalDate) = ExpenseEntity(
        description = "e", amountCentavos = amount, category = ExpenseCategory.RENT, incurredDate = date,
        paymentStatus = PaymentStatus.PAID, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
    )

    @Test
    fun summarizesProfitFromSalesCostsLossesAndExpenses() {
        val orders = listOf(
            order(subtotal = 30_000, discount = 6_000, cogs = 8_000, completed = "2026-10-03T01:00:00Z"),
            order(subtotal = 15_000, cogs = 4_000, completed = "2026-10-03T03:00:00Z", status = OrderStatus.REFUNDED),
        )
        val refunds = listOf(RefundEntity(orderId = 2, amountCentavos = 15_000, method = PaymentMethod.CASH, reason = "r", refundedAt = Instant.parse("2026-10-03T04:00:00Z")))
        val movements = mapOf(
            MovementType.REFUND_RETURN to 4_000L, // the refunded drink's ingredients went back
            MovementType.WASTAGE to -1_500L,
            MovementType.ADJUSTMENT to 500L, // a count found a little extra
        )
        val summary = ReportMath.summarize(orders, refunds, movements, listOf(expense(10_000, LocalDate.of(2026, 10, 3))))

        assertEquals(45_000L, summary.grossSalesCentavos)
        assertEquals(6_000L, summary.discountsCentavos)
        assertEquals(15_000L, summary.refundsCentavos)
        assertEquals(24_000L, summary.netSalesCentavos)
        assertEquals(8_000L, summary.cogsCentavos) // 12,000 − 4,000 returned
        assertEquals(16_000L, summary.grossProfitCentavos)
        assertEquals(1_000L, summary.stockLossCentavos) // 1,500 wasted − 500 found
        assertEquals(5_000L, summary.netProfitCentavos) // 16,000 − 1,000 − 10,000
        assertEquals(19_500L, summary.averageOrderCentavos) // (45,000 − 6,000) / 2
    }

    @Test
    fun loyaltyFreeDrinksLowerNetSales() {
        // ₱300 of drinks, one ₱120 drink free from the loyalty card.
        val free = order(subtotal = 30_000, cogs = 9_000, completed = "2026-10-03T01:00:00Z")
            .copy(loyaltyRewardCentavos = 12_000, totalCentavos = 18_000)
        val summary = ReportMath.summarize(listOf(free), emptyList(), emptyMap(), emptyList())

        assertEquals(12_000L, summary.loyaltyRewardsCentavos)
        assertEquals(18_000L, summary.netSalesCentavos)
        assertEquals(9_000L, summary.grossProfitCentavos)
        assertEquals(18_000L, summary.averageOrderCentavos)
    }

    @Test
    fun emptyPeriodHasNoAverage() {
        val summary = ReportMath.summarize(emptyList(), emptyList(), emptyMap(), emptyList())
        assertEquals(0L, summary.netProfitCentavos)
        assertNull(summary.averageOrderCentavos)
    }

    @Test
    fun dailyTotalsUseTheShopTimeZoneAndIncludeQuietDays() {
        val range = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))
        // 17:00 UTC on Oct 1 is 1:00 AM Oct 2 in Manila.
        val orders = listOf(order(subtotal = 10_000, cogs = 0, completed = "2026-10-01T17:00:00Z"))
        val days = ReportMath.dailyTotals(range, orders, emptyList(), listOf(expense(5_000, LocalDate.of(2026, 10, 3))), zone)

        assertEquals(3, days.size)
        assertEquals(listOf(0, 1, 0), days.map { it.orderCount })
        assertEquals(10_000L, days[1].netSalesCentavos)
        assertEquals(5_000L, days[2].expensesCentavos)
    }

    @Test
    fun periodsResolveToDateRanges() {
        val saturday = LocalDate.of(2026, 10, 3)
        assertEquals(DateRange.day(LocalDate.of(2026, 10, 2)), ReportPeriod.YESTERDAY.range(saturday))
        assertEquals(DateRange(LocalDate.of(2026, 9, 28), saturday), ReportPeriod.THIS_WEEK.range(saturday))
        assertEquals(DateRange(LocalDate.of(2026, 10, 1), saturday), ReportPeriod.THIS_MONTH.range(saturday))
        assertEquals(DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), ReportPeriod.LAST_MONTH.range(saturday))
        assertEquals(7, DateRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)).dayCount)
    }
}
