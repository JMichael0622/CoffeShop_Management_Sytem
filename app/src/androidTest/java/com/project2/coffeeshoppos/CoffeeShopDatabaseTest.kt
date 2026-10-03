package com.project2.coffeeshoppos

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.local.entity.CategoryEntity
import com.project2.coffeeshoppos.data.local.entity.ExpenseEntity
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.data.local.entity.ProductEntity
import com.project2.coffeeshoppos.data.local.entity.ProductVariationEntity
import com.project2.coffeeshoppos.data.local.entity.RecurringExpenseTemplateEntity
import com.project2.coffeeshoppos.data.local.entity.VariationRecipeItemEntity
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.domain.PaymentStatus
import com.project2.coffeeshoppos.domain.RecurrenceFrequency
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the phone against a temporary in-memory database (your real data is not touched). */
@RunWith(AndroidJUnit4::class)
class CoffeeShopDatabaseTest {
    private lateinit var db: CoffeeShopDatabase
    private val now = Instant.parse("2026-10-02T08:00:00Z")

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CoffeeShopDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun storesProductWithVariationAndExactCosts() = runBlocking<Unit> {
        val catalog = db.catalogDao()
        val categoryId = catalog.insertCategory(CategoryEntity(name = "Coffee", createdAt = now))
        val productId = catalog.insertProduct(
            ProductEntity(categoryId = categoryId, name = "Iced Latte", createdAt = now, updatedAt = now)
        )
        catalog.insertVariation(
            ProductVariationEntity(productId = productId, name = "Medium", priceCentavos = 15_000)
        )

        val ingredientId = db.inventoryDao().insertIngredient(
            IngredientEntity(
                name = "Coffee beans",
                baseUnit = MeasureUnit.GRAM,
                averageCostCentavosPerUnit = BigDecimal("1.234567"),
                createdAt = now,
                updatedAt = now
            )
        )

        assertEquals(listOf("Iced Latte"), catalog.observeActiveProducts().first().map { it.name })
        assertEquals(15_000L, catalog.observeVariations(productId).first().single().priceCentavos)
        assertEquals(
            BigDecimal("1.234567"),
            db.inventoryDao().getIngredient(ingredientId)!!.averageCostCentavosPerUnit
        )
    }

    @Test
    fun ingredientUsedInRecipeCannotBeDeleted() = runBlocking<Unit> {
        val catalog = db.catalogDao()
        val productId = catalog.insertProduct(
            ProductEntity(categoryId = null, name = "Latte", createdAt = now, updatedAt = now)
        )
        val variationId = catalog.insertVariation(
            ProductVariationEntity(productId = productId, name = "Regular", priceCentavos = 12_000)
        )
        val milkId = db.inventoryDao().insertIngredient(
            IngredientEntity(name = "Milk", baseUnit = MeasureUnit.MILLILITER, createdAt = now, updatedAt = now)
        )
        catalog.insertVariationRecipeItems(
            listOf(VariationRecipeItemEntity(variationId = variationId, ingredientId = milkId, quantityMilli = 180_000))
        )

        assertThrows(SQLiteConstraintException::class.java) {
            db.openHelper.writableDatabase.execSQL("DELETE FROM ingredients WHERE id = $milkId")
        }
    }

    @Test
    fun sameRecurringPeriodCannotBeRecordedTwice() = runBlocking<Unit> {
        val expenses = db.expenseDao()
        val templateId = expenses.insertTemplate(
            RecurringExpenseTemplateEntity(
                description = "Rent",
                amountCentavos = 1_500_000,
                category = ExpenseCategory.RENT,
                frequency = RecurrenceFrequency.MONTHLY,
                dueDay = 5,
                createdAt = now
            )
        )
        val october = ExpenseEntity(
            description = "Rent",
            amountCentavos = 1_500_000,
            category = ExpenseCategory.RENT,
            incurredDate = LocalDate.of(2026, 10, 5),
            paymentStatus = PaymentStatus.UNPAID,
            recurringTemplateId = templateId,
            recurringPeriodKey = "2026-10",
            createdAt = now,
            updatedAt = now
        )
        expenses.insertExpense(october)

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { expenses.insertExpense(october) }
        }
    }
}
