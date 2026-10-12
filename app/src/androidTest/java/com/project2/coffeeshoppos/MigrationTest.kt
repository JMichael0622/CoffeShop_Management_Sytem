package com.project2.coffeeshoppos

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades databases made by older versions of the app and checks no shop data is lost.
 * Every new database version needs a test here.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CoffeeShopDatabase::class.java
    )

    @Test
    fun version1To2KeepsDataAndAddsLoyalty() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO categories (id, name, sortOrder, isArchived, createdAt) VALUES (1, 'Coffee', 0, 0, 1000)")
            db.execSQL(
                """INSERT INTO orders (id, orderNumber, status, subtotalCentavos, discountCentavos, discountReason,
                    totalCentavos, cogsCentavos, note, createdAt, updatedAt, completedAt, cancelledAt)
                   VALUES (1, '261003-001', 'COMPLETED', 15000, 0, '', 15000, 4000, '', 1000, 1000, 1000, NULL)"""
            )
        }

        // Fails if the upgraded tables do not exactly match what version 2 expects.
        helper.runMigrationsAndValidate(dbName, 2, true).use { db ->
            db.query("SELECT earnsStamps FROM categories WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Existing categories earn stamps", 1, c.getInt(0))
            }
            db.query("SELECT totalCentavos, loyaltyRewardCentavos, customerId FROM orders WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(15000, c.getLong(0))
                assertEquals(0, c.getLong(1))
                assertTrue(c.isNull(2))
            }
            db.query("SELECT COUNT(*) FROM customers").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }

    /** The real app setup (with every migration registered) opens an upgraded file. */
    @Test
    fun appOpensUpgradedDatabase() = runBlocking {
        helper.createDatabase(dbName, 1).close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, CoffeeShopDatabase::class.java, dbName)
            .addMigrations(*CoffeeShopDatabase.MIGRATIONS)
            .build()
        try {
            assertNull(db.loyaltyDao().getSettings())
            assertEquals(0, db.orderDao().countOrdersBetween(java.time.Instant.EPOCH, java.time.Instant.now()))
        } finally {
            db.close()
        }
    }
}
