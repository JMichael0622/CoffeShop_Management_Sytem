package com.project2.coffeeshoppos

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.repository.AuditRepository
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.data.repository.ExpenseRepository
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.data.repository.OrderRepository
import com.project2.coffeeshoppos.data.repository.ReportRepository
import com.project2.coffeeshoppos.domain.CartLine
import com.project2.coffeeshoppos.domain.DateRange
import com.project2.coffeeshoppos.domain.Discount
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.ExpenseDraft
import com.project2.coffeeshoppos.domain.IngredientDraft
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.domain.OrderDraft
import com.project2.coffeeshoppos.domain.PaymentInput
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.ProductDraft
import com.project2.coffeeshoppos.domain.PurchaseDraft
import com.project2.coffeeshoppos.domain.RecipeLine
import com.project2.coffeeshoppos.domain.RecipeTarget
import com.project2.coffeeshoppos.domain.SizeInput
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A clock the test can move forward, to put sales on different days. */
private class TestClock(var now: Instant, private val zoneId: ZoneId) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = TestClock(now, zone)
}

/** Runs real sales, refunds, wastage and expenses through the repositories, then checks the report. */
@RunWith(AndroidJUnit4::class)
class ReportRepositoryTest {
    private lateinit var db: CoffeeShopDatabase
    private lateinit var clock: TestClock
    private lateinit var inventory: InventoryRepository
    private lateinit var orders: OrderRepository
    private lateinit var expenses: ExpenseRepository
    private lateinit var reports: ReportRepository
    private var latteId = 0L
    private var sizeId = 0L
    private var beansId = 0L

    private val today = LocalDate.of(2026, 10, 3)

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CoffeeShopDatabase::class.java).build()
        // Yesterday, 10:00 AM Manila.
        clock = TestClock(Instant.parse("2026-10-02T02:00:00Z"), ZoneId.of("Asia/Manila"))
        val audit = AuditRepository(db, clock)
        val catalog = CatalogRepository(db, audit, clock)
        inventory = InventoryRepository(db, audit, clock)
        orders = OrderRepository(db, inventory, audit, clock)
        expenses = ExpenseRepository(db, audit, clock)
        reports = ReportRepository(db, clock)

        // Beans at ₱1.00/g; a ₱100 latte uses 20 g (₱20 cost).
        beansId = inventory.addIngredient(IngredientDraft(0, "Beans", "", MeasureUnit.GRAM, 0, 1_000_000, 100_000))
        latteId = catalog.saveProduct(ProductDraft(0, "Latte", null, "", true, listOf(SizeInput(0, "Regular", 10_000)), emptySet()))
        sizeId = catalog.getProductForEdit(latteId)!!.sizes.single().id
        inventory.saveRecipe(RecipeTarget.Variation(sizeId), listOf(RecipeLine(beansId, 20_000)))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun sell(quantity: Int, discount: Discount = Discount.None, method: PaymentMethod = PaymentMethod.CASH): Long =
        orders.placeOrder(
            OrderDraft(
                lines = listOf(CartLine(1, latteId, sizeId, emptyMap(), quantity)),
                discount = discount,
                discountReason = if (discount == Discount.None) "" else "Promo",
                note = "",
                payment = PaymentInput(method, cashReceivedCentavos = if (method == PaymentMethod.CASH) 100_000 else null),
                completeNow = true
            )
        ).orderId

    @Test
    fun reportAddsUpEverythingThatHappenedToday() = runBlocking<Unit> {
        sell(1) // yesterday: must not appear in today's report

        clock.now = Instant.parse("2026-10-03T02:00:00Z") // today, 10:00 AM
        sell(3) // ₱300, cost ₱60
        sell(2, Discount.Percent(10), PaymentMethod.GCASH) // ₱200 − ₱20, cost ₱40
        val refunded = sell(1) // ₱100, cost ₱20, refunded below with stock returned
        orders.refundOrder(refunded, "Wrong drink", returnStock = true)
        val cancelled = orders.placeOrder(
            OrderDraft(listOf(CartLine(1, latteId, sizeId, emptyMap(), 5)), Discount.None, "", "", PaymentInput(PaymentMethod.CARD), completeNow = false)
        ).orderId
        orders.cancelOrder(cancelled, "Left") // never a sale
        inventory.recordWastage(beansId, 30_000, "Spilled") // ₱30 loss
        inventory.recordPurchase(PurchaseDraft(beansId, BigDecimal.ONE, MeasureUnit.KILOGRAM, 100_000, "", "", "", today))
        expenses.saveExpense(
            ExpenseDraft(0, "Electricity", 50_000, ExpenseCategory.ELECTRICITY, today, true, today, PaymentMethod.CASH, "", "")
        )

        val report = reports.observeReport(DateRange.day(today)).first()
        val s = report.summary
        assertEquals(60_000L, s.grossSalesCentavos)
        assertEquals(2_000L, s.discountsCentavos)
        assertEquals(10_000L, s.refundsCentavos)
        assertEquals(48_000L, s.netSalesCentavos)
        assertEquals(10_000L, s.cogsCentavos) // 120 g × ₱1 = ₱120, minus ₱20 returned
        assertEquals(3_000L, s.stockLossCentavos)
        assertEquals(50_000L, s.operatingExpensesCentavos)
        assertEquals(-15_000L, s.netProfitCentavos) // 480 − 100 − 30 − 500
        assertEquals(3, s.orderCount)
        assertEquals(1, report.cancelledCount)
        assertEquals(1, report.refundedCount)
        assertEquals(100_000L, report.purchasesCentavos)
        assertEquals(3_000L, report.wastageCentavos)
        // Refunded order left out of product sales: 3 + 2 lattes.
        assertEquals(5, report.productSales.single().quantity)
        assertEquals(mapOf(PaymentMethod.CASH to 40_000L, PaymentMethod.GCASH to 18_000L), report.salesByMethod.associate { it.method to it.amountCentavos })

        val twoDays = reports.observeReport(DateRange(today.minusDays(1), today)).first()
        assertEquals(4, twoDays.summary.orderCount)
        assertEquals(listOf(1, 3), twoDays.days.map { it.orderCount })
    }
}
