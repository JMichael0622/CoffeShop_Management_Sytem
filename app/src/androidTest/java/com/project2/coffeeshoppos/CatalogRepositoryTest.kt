package com.project2.coffeeshoppos

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.repository.AuditEntityType
import com.project2.coffeeshoppos.data.repository.AuditRepository
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.domain.AddOnDraft
import com.project2.coffeeshoppos.domain.ProductDraft
import com.project2.coffeeshoppos.domain.SizeInput
import com.project2.coffeeshoppos.domain.UserFacingException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the phone against a temporary in-memory database (your real data is not touched). */
@RunWith(AndroidJUnit4::class)
class CatalogRepositoryTest {
    private lateinit var db: CoffeeShopDatabase
    private lateinit var audit: AuditRepository
    private lateinit var repository: CatalogRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CoffeeShopDatabase::class.java).build()
        val clock = Clock.fixed(Instant.parse("2026-10-02T08:00:00Z"), ZoneId.of("Asia/Manila"))
        audit = AuditRepository(db, clock)
        repository = CatalogRepository(db, audit, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun latte(id: Long = 0, sizes: List<SizeInput>, categoryId: Long? = null, addOns: Set<Long> = emptySet()) =
        ProductDraft(
            id = id, name = "Iced Latte", categoryId = categoryId, description = "",
            isAvailable = true, sizes = sizes, addOnIds = addOns
        )

    @Test
    fun removedSizeIsArchivedNotDeletedAndChangesAreAudited() = runBlocking {
        val productId = repository.saveProduct(
            latte(sizes = listOf(SizeInput(0, "12 oz", 12_000), SizeInput(0, "16 oz", 15_000)))
        )
        val sizes = repository.getProductForEdit(productId)!!.sizes
        val twelve = sizes.first { it.name == "12 oz" }

        // Keep only 12 oz and raise its price.
        repository.saveProduct(latte(id = productId, sizes = listOf(SizeInput(twelve.id, "12 oz", 13_000))))

        val all = db.catalogDao().getVariations(productId)
        assertEquals(2, all.size) // the 16 oz row still exists for old orders...
        assertTrue(all.first { it.name == "16 oz" }.isArchived) // ...but is archived
        assertEquals(13_000L, all.first { it.name == "12 oz" }.priceCentavos)

        val records = audit.observeFor(AuditEntityType.PRODUCT, productId).first()
        assertEquals(2, records.size)
        val update = records.first { it.summary.startsWith("Updated") }
        assertTrue(update.details.contains("Removed size \"16 oz\""))
        assertTrue(update.details.contains("₱120.00 → ₱130.00"))
    }

    @Test
    fun categoryWithActiveProductsCannotBeArchived() = runBlocking {
        val coffee = repository.addCategory("Coffee")
        val productId = repository.saveProduct(latte(sizes = listOf(SizeInput(0, "Regular", 12_000)), categoryId = coffee))

        assertThrows(UserFacingException::class.java) {
            runBlocking { repository.setCategoryArchived(coffee, true) }
        }

        repository.setProductArchived(productId, true)
        repository.setCategoryArchived(coffee, true)
        assertTrue(db.catalogDao().getCategory(coffee)!!.isArchived)
    }

    @Test
    fun duplicateNamesAreRejectedIgnoringCase() = runBlocking {
        repository.addCategory("Coffee")
        assertThrows(UserFacingException::class.java) {
            runBlocking { repository.addCategory("  coffee ") }
        }
        repository.saveAddOn(AddOnDraft(0, "Extra shot", 3_000, true))
        assertThrows(UserFacingException::class.java) {
            runBlocking { repository.saveAddOn(AddOnDraft(0, "EXTRA SHOT", 3_000, true)) }
        }
    }

    @Test
    fun productAddOnLinksAreSaved() = runBlocking {
        val shot = repository.saveAddOn(AddOnDraft(0, "Extra shot", 3_000, true))
        val oat = repository.saveAddOn(AddOnDraft(0, "Oat milk", 4_000, true))
        val productId = repository.saveProduct(latte(sizes = listOf(SizeInput(0, "Regular", 12_000)), addOns = setOf(shot, oat)))
        assertEquals(setOf(shot, oat), repository.getProductForEdit(productId)!!.addOnIds)

        repository.saveProduct(latte(id = productId, sizes = repository.getProductForEdit(productId)!!.sizes
            .map { SizeInput(it.id, it.name, it.priceCentavos) }, addOns = setOf(oat)))
        assertEquals(setOf(oat), repository.getProductForEdit(productId)!!.addOnIds)
    }

    @Test
    fun categoriesCanBeReordered() = runBlocking {
        val coffee = repository.addCategory("Coffee")
        val tea = repository.addCategory("Tea")
        repository.moveCategory(tea, up = true)
        assertEquals(listOf(tea, coffee), repository.observeCategories().first().map { it.id })
    }
}
