package com.project2.coffeeshoppos

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.repository.AuditEntityType
import com.project2.coffeeshoppos.data.repository.AuditRepository
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.domain.AddOnDraft
import com.project2.coffeeshoppos.domain.IngredientDraft
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.domain.MovementType
import com.project2.coffeeshoppos.domain.ProductDraft
import com.project2.coffeeshoppos.domain.PurchaseDraft
import com.project2.coffeeshoppos.domain.RecipeLine
import com.project2.coffeeshoppos.domain.RecipeTarget
import com.project2.coffeeshoppos.domain.SizeInput
import com.project2.coffeeshoppos.domain.UserFacingException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the phone against a temporary in-memory database (your real data is not touched). */
@RunWith(AndroidJUnit4::class)
class InventoryRepositoryTest {
    private lateinit var db: CoffeeShopDatabase
    private lateinit var audit: AuditRepository
    private lateinit var catalog: CatalogRepository
    private lateinit var repository: InventoryRepository
    private val today = LocalDate.of(2026, 10, 3)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CoffeeShopDatabase::class.java).build()
        val clock = Clock.fixed(Instant.parse("2026-10-03T02:00:00Z"), ZoneId.of("Asia/Manila"))
        audit = AuditRepository(db, clock)
        catalog = CatalogRepository(db, audit, clock)
        repository = InventoryRepository(db, audit, clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun beans(openingGrams: Long = 0, openingCost: Long = 0) = repository.addIngredient(
        IngredientDraft(
            id = 0, name = "Espresso beans", category = "Coffee", baseUnit = MeasureUnit.GRAM,
            reorderLevelMilli = 500_000, openingQuantityMilli = openingGrams * 1_000, openingTotalCostCentavos = openingCost
        )
    )

    private fun purchase(id: Long, kg: String, totalCentavos: Long, on: LocalDate = today) = PurchaseDraft(
        ingredientId = id, enteredQuantity = BigDecimal(kg), enteredUnit = MeasureUnit.KILOGRAM,
        totalCostCentavos = totalCentavos, supplier = "Bean Co", reference = "OR-1", notes = "", purchasedOn = on
    )

    /** The ledger must always add up to the stock on hand. */
    private suspend fun assertLedgerMatches(id: Long) {
        val ingredient = repository.getIngredient(id)!!
        val movements = repository.observeMovements(id).first()
        assertEquals(ingredient.quantityOnHandMilli, movements.sumOf { it.quantityChangeMilli })
        assertEquals(ingredient.quantityOnHandMilli, movements.first().quantityAfterMilli)
    }

    @Test
    fun openingStockAndPurchasesUpdateStockAndAverageCost() = runBlocking<Unit> {
        val id = beans(openingGrams = 1_000, openingCost = 90_000) // 1 kg for ₱900
        repository.recordPurchase(purchase(id, "1", 110_000)) // 1 kg for ₱1,100

        val ingredient = repository.getIngredient(id)!!
        assertEquals(2_000_000L, ingredient.quantityOnHandMilli)
        assertEquals(0, BigDecimal("100").compareTo(ingredient.averageCostCentavosPerUnit)) // ₱1.00/g

        val movements = repository.observeMovements(id).first()
        assertEquals(listOf(MovementType.PURCHASE, MovementType.OPENING_BALANCE), movements.map { it.type })
        assertEquals(110_000L, movements.first().costCentavos)
        assertEquals("Bought from Bean Co · Ref OR-1", movements.first().reason)
        assertLedgerMatches(id)

        val purchases = db.inventoryDao().observePurchases(id).first()
        assertEquals(1, purchases.size)
        assertEquals(1_000_000L, purchases.single().quantityMilli)
    }

    @Test
    fun backdatedPurchaseIsDatedAtNoonAndFutureDatesAreRejected() = runBlocking<Unit> {
        val id = beans()
        repository.recordPurchase(purchase(id, "1", 90_000, on = today.minusDays(2)))
        val purchased = db.inventoryDao().observePurchases(id).first().single().purchasedAt
        assertEquals(Instant.parse("2026-10-01T04:00:00Z"), purchased) // noon in Manila

        assertThrows(UserFacingException::class.java) {
            runBlocking { repository.recordPurchase(purchase(id, "1", 90_000, on = today.plusDays(1))) }
        }
    }

    @Test
    fun wastageRemovesStockAtAverageCostButNotMoreThanOnHand() = runBlocking<Unit> {
        val id = beans(openingGrams = 1_000, openingCost = 90_000)
        repository.recordWastage(id, 100_000, "Spilled")

        val ingredient = repository.getIngredient(id)!!
        assertEquals(900_000L, ingredient.quantityOnHandMilli)
        assertEquals(-9_000L, repository.observeMovements(id).first().first().costCentavos) // 100 g x ₱0.90
        assertThrows(UserFacingException::class.java) {
            runBlocking { repository.recordWastage(id, 1_000_000, "Too much") }
        }
        assertLedgerMatches(id)
    }

    @Test
    fun stockCountRecordsOnlyTheDifference() = runBlocking<Unit> {
        val id = beans(openingGrams = 1_000, openingCost = 90_000)
        assertEquals(0L, repository.recordStockCount(id, 1_000_000, ""))
        assertEquals(1, repository.observeMovements(id).first().size) // nothing written for a matching count

        assertEquals(-250_000L, repository.recordStockCount(id, 750_000, "Weekly count"))
        val adjustment = repository.observeMovements(id).first().first()
        assertEquals(MovementType.ADJUSTMENT, adjustment.type)
        assertEquals(-22_500L, adjustment.costCentavos)
        assertEquals(750_000L, repository.getIngredient(id)!!.quantityOnHandMilli)
        assertLedgerMatches(id)
    }

    @Test
    fun recipesAreSavedAuditedAndBlockArchivingTheirIngredients() = runBlocking<Unit> {
        val beansId = beans(openingGrams = 1_000, openingCost = 90_000)
        val productId = catalog.saveProduct(
            ProductDraft(0, "Americano", null, "", true, listOf(SizeInput(0, "12 oz", 12_000)), emptySet())
        )
        val sizeId = catalog.getProductForEdit(productId)!!.sizes.single().id
        val target = RecipeTarget.Variation(sizeId)

        repository.saveRecipe(target, listOf(RecipeLine(beansId, 18_000)))
        assertEquals(listOf(RecipeLine(beansId, 18_000)), repository.getRecipeForEdit(target)!!.lines)
        assertEquals(listOf("Americano · 12 oz"), repository.observeRecipeUsage(beansId).first())
        assertTrue(audit.observeFor(AuditEntityType.PRODUCT, productId).first().any { it.summary.startsWith("Updated recipe") })

        val row = repository.observeRecipeOverview().first().products.single().sizes.single()
        assertEquals(1_620L, row.costCentavos) // 18 g x ₱0.90
        assertFalse(row.hasUncostedIngredient)

        assertThrows(UserFacingException::class.java) {
            runBlocking { repository.setIngredientArchived(beansId, true) }
        }
        repository.saveRecipe(target, emptyList())
        repository.setIngredientArchived(beansId, true)
        assertTrue(repository.getIngredient(beansId)!!.isArchived)
    }

    @Test
    fun recipeRejectsDuplicatesAndZeroQuantities() = runBlocking<Unit> {
        val beansId = beans()
        val addOnId = catalog.saveAddOn(AddOnDraft(0, "Extra shot", 3_000, true))
        val target = RecipeTarget.AddOn(addOnId)
        assertThrows(UserFacingException::class.java) {
            runBlocking { repository.saveRecipe(target, listOf(RecipeLine(beansId, 9_000), RecipeLine(beansId, 9_000))) }
        }
        assertThrows(UserFacingException::class.java) {
            runBlocking { repository.saveRecipe(target, listOf(RecipeLine(beansId, 0))) }
        }
        repository.saveRecipe(target, listOf(RecipeLine(beansId, 18_000)))
        assertEquals(listOf("Extra shot"), repository.observeRecipeUsage(beansId).first())
    }

    @Test
    fun unitIsLockedOnceStockIsRecorded() = runBlocking<Unit> {
        val id = beans()
        assertTrue(repository.canChangeUnit(id))
        repository.recordPurchase(purchase(id, "1", 90_000))
        assertFalse(repository.canChangeUnit(id))
        val ingredient = repository.getIngredient(id)!!
        assertThrows(UserFacingException::class.java) {
            runBlocking {
                repository.updateIngredient(
                    IngredientDraft(id, ingredient.name, ingredient.category, MeasureUnit.MILLILITER, ingredient.reorderLevelMilli)
                )
            }
        }
    }

    @Test
    fun duplicateIngredientNamesAreRejectedIgnoringCase() = runBlocking<Unit> {
        beans()
        assertThrows(UserFacingException::class.java) {
            runBlocking {
                repository.addIngredient(IngredientDraft(0, " ESPRESSO beans ", "", MeasureUnit.GRAM, 0))
            }
        }
    }
}
