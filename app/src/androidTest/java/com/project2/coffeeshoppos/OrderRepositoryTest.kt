package com.project2.coffeeshoppos

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.repository.AuditEntityType
import com.project2.coffeeshoppos.data.repository.AuditRepository
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.data.repository.OrderRepository
import com.project2.coffeeshoppos.domain.AddOnDraft
import com.project2.coffeeshoppos.domain.CartLine
import com.project2.coffeeshoppos.domain.Discount
import com.project2.coffeeshoppos.domain.IngredientDraft
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.domain.MovementType
import com.project2.coffeeshoppos.domain.OrderDraft
import com.project2.coffeeshoppos.domain.OrderStatus
import com.project2.coffeeshoppos.domain.PaymentInput
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.ProductDraft
import com.project2.coffeeshoppos.domain.RecipeLine
import com.project2.coffeeshoppos.domain.RecipeTarget
import com.project2.coffeeshoppos.domain.SizeInput
import com.project2.coffeeshoppos.domain.UserFacingException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the phone against a temporary in-memory database (your real data is not touched). */
@RunWith(AndroidJUnit4::class)
class OrderRepositoryTest {
    private lateinit var db: CoffeeShopDatabase
    private lateinit var audit: AuditRepository
    private lateinit var catalog: CatalogRepository
    private lateinit var inventory: InventoryRepository
    private lateinit var orders: OrderRepository

    // Set up in each test: a latte (12 oz ₱120, 16 oz ₱150) using 18 g beans and 200 ml milk,
    // and an extra shot add-on (₱30) using 18 g beans.
    private var latteId = 0L
    private var twelveOz = 0L
    private var sixteenOz = 0L
    private var shotId = 0L
    private var beansId = 0L
    private var milkId = 0L

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CoffeeShopDatabase::class.java).build()
        val clock = Clock.fixed(Instant.parse("2026-10-03T02:00:00Z"), ZoneId.of("Asia/Manila"))
        audit = AuditRepository(db, clock)
        catalog = CatalogRepository(db, audit, clock)
        inventory = InventoryRepository(db, audit, clock)
        orders = OrderRepository(db, inventory, audit, clock)

        // 1 kg beans for ₱900 (₱0.90/g), 1 L milk for ₱100 (₱0.10/ml).
        beansId = inventory.addIngredient(IngredientDraft(0, "Beans", "", MeasureUnit.GRAM, 0, 1_000_000, 90_000))
        milkId = inventory.addIngredient(IngredientDraft(0, "Milk", "", MeasureUnit.MILLILITER, 0, 1_000_000, 10_000))
        shotId = catalog.saveAddOn(AddOnDraft(0, "Extra shot", 3_000, true))
        latteId = catalog.saveProduct(
            ProductDraft(0, "Iced Latte", null, "", true, listOf(SizeInput(0, "12 oz", 12_000), SizeInput(0, "16 oz", 15_000)), setOf(shotId))
        )
        val sizes = catalog.getProductForEdit(latteId)!!.sizes
        twelveOz = sizes.first { it.name == "12 oz" }.id
        sixteenOz = sizes.first { it.name == "16 oz" }.id
        listOf(twelveOz, sixteenOz).forEach {
            inventory.saveRecipe(RecipeTarget.Variation(it), listOf(RecipeLine(beansId, 18_000), RecipeLine(milkId, 200_000)))
        }
        inventory.saveRecipe(RecipeTarget.AddOn(shotId), listOf(RecipeLine(beansId, 18_000)))
    }

    @After
    fun tearDown() = db.close()

    private fun cash(received: Long) = PaymentInput(PaymentMethod.CASH, cashReceivedCentavos = received)

    private fun draft(
        lines: List<CartLine>,
        payment: PaymentInput = cash(100_000),
        discount: Discount = Discount.None,
        reason: String = "",
        completeNow: Boolean = false,
    ) = OrderDraft(lines, discount, reason, note = "", payment = payment, completeNow = completeNow)

    // 2 × 16 oz with one extra shot each = 2 × ₱180 = ₱360
    private val twoBigLattesWithShot get() = CartLine(1, latteId, sixteenOz, mapOf(shotId to 1), quantity = 2)

    @Test
    fun placingAnOrderSnapshotsPricesAndGivesChange() = runBlocking<Unit> {
        val placed = orders.placeOrder(draft(listOf(twoBigLattesWithShot), payment = cash(50_000)))
        assertEquals("261003-001", placed.orderNumber)
        assertEquals(36_000L, placed.totalCentavos)
        assertEquals(14_000L, placed.changeCentavos)

        val detail = orders.observeOrderDetail(placed.orderId).first()!!
        assertEquals(OrderStatus.PENDING, detail.order.status)
        val line = detail.lines.single()
        assertEquals("16 oz", line.line.variationName)
        assertEquals(36_000L, line.line.lineTotalCentavos)
        assertEquals("Extra shot", line.addOns.single().addOnName)
        assertEquals(50_000L, detail.payments.single().cashReceivedCentavos)

        // Paid but not handed over: no stock used yet.
        assertEquals(1_000_000L, inventory.getIngredient(beansId)!!.quantityOnHandMilli)
        assertEquals("261003-002", orders.placeOrder(draft(listOf(twoBigLattesWithShot))).orderNumber)
    }

    @Test
    fun completingTakesStockOutAndFixesCostOfGoods() = runBlocking<Unit> {
        val placed = orders.placeOrder(draft(listOf(twoBigLattesWithShot)))
        orders.startOrder(placed.orderId)
        orders.completeOrder(placed.orderId)

        // Beans: 2 × (18 g + 18 g shot) = 72 g; milk: 2 × 200 ml = 400 ml.
        assertEquals(928_000L, inventory.getIngredient(beansId)!!.quantityOnHandMilli)
        assertEquals(600_000L, inventory.getIngredient(milkId)!!.quantityOnHandMilli)

        val order = orders.observeOrderDetail(placed.orderId).first()!!
        // 72 g × ₱0.90 = ₱64.80, 400 ml × ₱0.10 = ₱40.00
        assertEquals(10_480L, order.order.cogsCentavos)
        assertEquals(10_480L, order.lines.single().line.cogsCentavos)
        assertEquals(OrderStatus.COMPLETED, order.order.status)
        assertEquals(MovementType.SALE, inventory.observeMovements(beansId).first().first().type)

        assertThrows(UserFacingException::class.java) { runBlocking { orders.completeOrder(placed.orderId) } }
    }

    @Test
    fun handOverNowCompletesInOneStep() = runBlocking<Unit> {
        val placed = orders.placeOrder(draft(listOf(CartLine(1, latteId, twelveOz, emptyMap(), 1)), completeNow = true))
        assertEquals(OrderStatus.COMPLETED, orders.observeOrderDetail(placed.orderId).first()!!.order.status)
        assertEquals(982_000L, inventory.getIngredient(beansId)!!.quantityOnHandMilli)
        assertTrue(orders.observeActiveOrders().first().isEmpty())
    }

    @Test
    fun cancellingRefundsThePaymentWithoutTouchingStock() = runBlocking<Unit> {
        val placed = orders.placeOrder(draft(listOf(twoBigLattesWithShot), payment = PaymentInput(PaymentMethod.GCASH, reference = "123")))
        orders.cancelOrder(placed.orderId, "Customer left")

        val detail = orders.observeOrderDetail(placed.orderId).first()!!
        assertEquals(OrderStatus.CANCELLED, detail.order.status)
        assertEquals(36_000L, detail.refunds.single().amountCentavos)
        assertEquals(PaymentMethod.GCASH, detail.refunds.single().method)
        assertEquals(1_000_000L, inventory.getIngredient(beansId)!!.quantityOnHandMilli)
        assertTrue(audit.observeFor(AuditEntityType.ORDER, placed.orderId).first().isNotEmpty())
    }

    @Test
    fun refundCanReturnIngredientsToStock() = runBlocking<Unit> {
        val placed = orders.placeOrder(draft(listOf(twoBigLattesWithShot), completeNow = true))
        orders.refundOrder(placed.orderId, "Wrong order, not made", returnStock = true)

        assertEquals(OrderStatus.REFUNDED, orders.observeOrderDetail(placed.orderId).first()!!.order.status)
        val beans = inventory.getIngredient(beansId)!!
        assertEquals(1_000_000L, beans.quantityOnHandMilli)
        assertEquals(0, java.math.BigDecimal("90").compareTo(beans.averageCostCentavosPerUnit))
        assertEquals(MovementType.REFUND_RETURN, inventory.observeMovements(beansId).first().first().type)

        assertThrows(UserFacingException::class.java) {
            runBlocking { orders.refundOrder(placed.orderId, "again", returnStock = true) }
        }
    }

    @Test
    fun refundWithoutReturningStockLeavesStockUsed() = runBlocking<Unit> {
        val placed = orders.placeOrder(draft(listOf(twoBigLattesWithShot), completeNow = true))
        orders.refundOrder(placed.orderId, "Spilled", returnStock = false)
        assertEquals(928_000L, inventory.getIngredient(beansId)!!.quantityOnHandMilli)
    }

    @Test
    fun discountsNeedAReasonAndCashMustCoverTheTotal() = runBlocking<Unit> {
        val line = listOf(CartLine(1, latteId, twelveOz, emptyMap(), 1))
        assertThrows(UserFacingException::class.java) {
            runBlocking { orders.placeOrder(draft(line, discount = Discount.Percent(20))) }
        }
        assertThrows(UserFacingException::class.java) {
            runBlocking { orders.placeOrder(draft(line, payment = cash(11_999))) }
        }
        val placed = orders.placeOrder(draft(line, payment = cash(9_600), discount = Discount.Percent(20), reason = "Senior citizen"))
        assertEquals(9_600L, placed.totalCentavos)
        assertEquals(0L, placed.changeCentavos)
    }

    @Test
    fun soldOutProductsCannotBeOrdered() = runBlocking<Unit> {
        catalog.setProductAvailable(latteId, false)
        assertThrows(UserFacingException::class.java) {
            runBlocking { orders.placeOrder(draft(listOf(CartLine(1, latteId, twelveOz, emptyMap(), 1)))) }
        }
        assertTrue(catalog.observePosMenu().first().products.none { it.id == latteId })
    }

    @Test
    fun nonCashPaymentsHaveNoChange() = runBlocking<Unit> {
        val placed = orders.placeOrder(
            draft(listOf(CartLine(1, latteId, twelveOz, emptyMap(), 1)), payment = PaymentInput(PaymentMethod.CARD, cashReceivedCentavos = 50_000))
        )
        assertNull(placed.changeCentavos)
        assertNull(orders.observeOrderDetail(placed.orderId).first()!!.payments.single().cashReceivedCentavos)
        assertEquals(1, orders.observeOrdersOn(LocalDate.of(2026, 10, 3)).first().size)
    }
}
