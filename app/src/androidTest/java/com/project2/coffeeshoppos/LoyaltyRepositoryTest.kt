package com.project2.coffeeshoppos

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project2.coffeeshoppos.data.export.ExportRepository
import com.project2.coffeeshoppos.data.export.ExportType
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.local.entity.CategoryEntity
import com.project2.coffeeshoppos.data.repository.AuditRepository
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.data.repository.LoyaltyRepository
import com.project2.coffeeshoppos.data.repository.OrderRepository
import com.project2.coffeeshoppos.data.repository.ReportRepository
import com.project2.coffeeshoppos.domain.CartLine
import com.project2.coffeeshoppos.domain.DateRange
import com.project2.coffeeshoppos.domain.Discount
import com.project2.coffeeshoppos.domain.LoyaltyEntryType
import com.project2.coffeeshoppos.domain.LoyaltyRules
import com.project2.coffeeshoppos.domain.OrderDraft
import com.project2.coffeeshoppos.domain.PaymentInput
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.ProductDraft
import com.project2.coffeeshoppos.domain.SizeInput
import com.project2.coffeeshoppos.domain.UserFacingException
import java.io.File
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
class LoyaltyRepositoryTest {
    private lateinit var db: CoffeeShopDatabase
    private lateinit var loyalty: LoyaltyRepository
    private lateinit var catalog: CatalogRepository
    private lateinit var orders: OrderRepository
    private lateinit var clock: Clock

    // A latte (12 oz ₱120, 16 oz ₱150) that earns stamps, and a cookie (₱65) in a
    // "Pastry" category that does not.
    private var latteId = 0L
    private var twelveOz = 0L
    private var sixteenOz = 0L
    private var cookieId = 0L
    private var cookieSize = 0L
    private var anaId = 0L

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CoffeeShopDatabase::class.java).build()
        val now = Instant.parse("2026-10-06T02:00:00Z")
        clock = Clock.fixed(now, ZoneId.of("Asia/Manila"))
        val audit = AuditRepository(db, clock)
        catalog = CatalogRepository(db, audit, clock)
        loyalty = LoyaltyRepository(db, audit, clock)
        orders = OrderRepository(db, InventoryRepository(db, audit, clock), loyalty, audit, clock)

        latteId = catalog.saveProduct(
            ProductDraft(0, "Iced Latte", null, "", true, listOf(SizeInput(0, "12 oz", 12_000), SizeInput(0, "16 oz", 15_000)), emptySet())
        )
        val sizes = catalog.getProductForEdit(latteId)!!.sizes
        twelveOz = sizes.first { it.name == "12 oz" }.id
        sixteenOz = sizes.first { it.name == "16 oz" }.id

        val pastryId = db.catalogDao().insertCategory(CategoryEntity(name = "Pastry", earnsStamps = false, createdAt = now))
        cookieId = catalog.saveProduct(ProductDraft(0, "Cookie", pastryId, "", true, listOf(SizeInput(0, "Regular", 6_500)), emptySet()))
        cookieSize = catalog.getProductForEdit(cookieId)!!.sizes.single().id

        anaId = loyalty.addCustomer("Ana Cruz", "0917 123 4567")
    }

    @After
    fun tearDown() = db.close()

    private fun latte(quantity: Int, key: Long = 1, size: Long = twelveOz) = CartLine(key, latteId, size, emptyMap(), quantity)
    private fun cookie(quantity: Int, key: Long = 2) = CartLine(key, cookieId, cookieSize, emptyMap(), quantity)

    private fun draft(
        lines: List<CartLine>,
        customerId: Long? = anaId,
        rewardLineKey: Long? = null,
        discount: Discount = Discount.None,
        reason: String = "",
        completeNow: Boolean = false,
    ) = OrderDraft(
        lines, discount, reason, note = "",
        payment = PaymentInput(PaymentMethod.GCASH),
        completeNow = completeNow, customerId = customerId, rewardLineKey = rewardLineKey
    )

    private suspend fun stamps() = loyalty.observeCustomer(anaId).first()!!.stamps

    @Test
    fun stampsAreEarnedWhenTheOrderIsHandedOver() = runBlocking<Unit> {
        val placed = orders.placeOrder(draft(listOf(latte(3), cookie(1))))
        assertEquals("Nothing earned while the order is only paid", 0, stamps())

        orders.completeOrder(placed.orderId)
        assertEquals("3 lattes earn 3 stamps; the cookie earns none", 3, stamps())
        assertEquals(1, loyalty.observeCustomer(anaId).first()!!.visits)
    }

    @Test
    fun freeDrinkIsCappedAndDiscountAppliesToTheRest() = runBlocking<Unit> {
        loyalty.saveRules(LoyaltyRules(isEnabled = true, stampsForReward = 10, rewardCapCentavos = 13_000))
        loyalty.adjustStamps(anaId, 10, "Paper card")

        // 2 × 16 oz (₱150 each), one free but capped at ₱130, then 20% senior discount on the rest.
        val placed = orders.placeOrder(
            draft(listOf(latte(2, size = sixteenOz)), rewardLineKey = 1, discount = Discount.Percent(20), reason = "Senior citizen")
        )
        val order = orders.observeOrderDetail(placed.orderId).first()!!.order
        assertEquals(30_000L, order.subtotalCentavos)
        assertEquals(13_000L, order.loyaltyRewardCentavos)
        assertEquals(3_400L, order.discountCentavos) // 20% of ₱170
        assertEquals(13_600L, order.totalCentavos)
        assertEquals("The free drink is taken off the card as soon as it is used", 0, stamps())

        orders.completeOrder(placed.orderId)
        assertEquals("The free drink itself earns no stamp", 1, stamps())
    }

    @Test
    fun cancellingGivesTheFreeDrinkBack() = runBlocking<Unit> {
        loyalty.adjustStamps(anaId, 10, "Paper card")
        val placed = orders.placeOrder(draft(listOf(latte(1)), rewardLineKey = 1))
        assertEquals(0, stamps())

        orders.cancelOrder(placed.orderId, "Customer left")
        assertEquals(10, stamps())
        val types = loyalty.observeEntries(anaId).first().map { it.type }
        assertEquals(listOf(LoyaltyEntryType.REDEEM_RETURNED, LoyaltyEntryType.REDEEMED, LoyaltyEntryType.ADJUSTMENT), types)
    }

    @Test
    fun refundingTakesEarnedStampsBack() = runBlocking<Unit> {
        val placed = orders.placeOrder(draft(listOf(latte(3)), completeNow = true))
        assertEquals(3, stamps())

        orders.refundOrder(placed.orderId, "Wrong order", returnStock = false)
        assertEquals(0, stamps())
    }

    @Test
    fun freeDrinkNeedsEnoughStampsAndADrinkThatEarnsStamps() = runBlocking<Unit> {
        loyalty.adjustStamps(anaId, 9, "Paper card")
        assertThrows(UserFacingException::class.java) {
            runBlocking { orders.placeOrder(draft(listOf(latte(1)), rewardLineKey = 1)) }
        }
        loyalty.adjustStamps(anaId, 1, "Paper card")
        assertThrows(UserFacingException::class.java) {
            runBlocking { orders.placeOrder(draft(listOf(cookie(1)), rewardLineKey = 2)) }
        }
        assertThrows(UserFacingException::class.java) {
            runBlocking { orders.placeOrder(draft(listOf(latte(1)), customerId = null, rewardLineKey = 1)) }
        }
        assertEquals("Failed orders change nothing", 10, stamps())
    }

    @Test
    fun noStampsWhileTheCardIsTurnedOff() = runBlocking<Unit> {
        loyalty.saveRules(LoyaltyRules.DEFAULT.copy(isEnabled = false))
        orders.placeOrder(draft(listOf(latte(2)), completeNow = true))
        assertEquals(0, stamps())
    }

    @Test
    fun customersAreFoundByEitherWayOfWritingTheirNumber() = runBlocking<Unit> {
        assertEquals(anaId, loyalty.searchCustomers("+63 917 123").first().single().customer.id)
        assertEquals(anaId, loyalty.searchCustomers("ana").first().single().customer.id)
        assertThrows(UserFacingException::class.java) {
            runBlocking { loyalty.addCustomer("Someone Else", "+639171234567") }
        }
        assertThrows(UserFacingException::class.java) { runBlocking { loyalty.addCustomer("Ben", "1234") } }
    }

    @Test
    fun deletingACustomerRemovesTheirDetailsButKeepsHistory() = runBlocking<Unit> {
        val placed = orders.placeOrder(draft(listOf(latte(1)), completeNow = true))
        loyalty.deleteCustomer(anaId)

        val deleted = loyalty.observeCustomer(anaId).first()!!.customer
        assertNull(deleted.phone)
        assertEquals("Deleted customer", deleted.name)
        assertEquals(anaId, orders.observeOrderDetail(placed.orderId).first()!!.order.customerId)
        assertEquals(emptyList<Any>(), loyalty.searchCustomers("").first())
        // The number is free to use again.
        loyalty.addCustomer("Ana Cruz", "09171234567")
    }

    @Test
    fun exportsCustomersCsv() = runBlocking<Unit> {
        orders.placeOrder(draft(listOf(latte(2)), completeNow = true))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "customers-test.csv")
        val exports = ExportRepository(context, db, ReportRepository(db, clock), clock)

        assertEquals(1, exports.export(ExportType.CUSTOMERS_CSV, DateRange.day(LocalDate.of(2026, 10, 6)), Uri.fromFile(file)))
        val lines = file.readText().removePrefix("﻿").split("\r\n")
        assertTrue(lines[0].startsWith("Name,Mobile number,Stamps,Visits,Total spent"))
        assertTrue(lines[1], lines[1].startsWith("Ana Cruz,0917 123 4567,2,1,240.00"))
        file.delete()
    }

    @Test
    fun rulesAreChecked() = runBlocking<Unit> {
        assertThrows(UserFacingException::class.java) {
            runBlocking { loyalty.saveRules(LoyaltyRules(true, stampsForReward = 1, rewardCapCentavos = 15_000)) }
        }
        assertThrows(UserFacingException::class.java) {
            runBlocking { loyalty.saveRules(LoyaltyRules(true, stampsForReward = 10, rewardCapCentavos = 0)) }
        }
        assertEquals(LoyaltyRules.DEFAULT, loyalty.getRules())
    }
}
