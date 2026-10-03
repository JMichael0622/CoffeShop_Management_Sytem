package com.project2.coffeeshoppos

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project2.coffeeshoppos.data.backup.BackupManager
import com.project2.coffeeshoppos.data.export.ExportRepository
import com.project2.coffeeshoppos.data.export.ExportType
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.repository.AuditRepository
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.data.repository.OrderRepository
import com.project2.coffeeshoppos.data.repository.ReportRepository
import com.project2.coffeeshoppos.domain.CartLine
import com.project2.coffeeshoppos.domain.DateRange
import com.project2.coffeeshoppos.domain.Discount
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exports and backs up a real file-based database. Uses its own database name, preferences and
 * folder, so the shop's real data on the test phone is never read or replaced.
 */
@RunWith(AndroidJUnit4::class)
class DataExportBackupTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = Clock.fixed(Instant.parse("2026-10-03T02:00:00Z"), ZoneId.of("Asia/Manila"))
    private val today = LocalDate.of(2026, 10, 3)
    private val dir = File(context.cacheDir, "data-test").apply { mkdirs() }
    private lateinit var db: CoffeeShopDatabase
    private lateinit var backups: BackupManager

    @Before
    fun setUp() {
        context.deleteDatabase(DB_NAME)
        db = openDb()
        backups = BackupManager(context, { db }, clock, databaseName = DB_NAME, prefsName = "backup_state_test")
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
        backups.safetyCopy.delete()
        dir.deleteRecursively()
        context.getSharedPreferences("backup_state_test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun openDb() = Room.databaseBuilder(context, CoffeeShopDatabase::class.java, DB_NAME).build()

    private suspend fun sellLatte(database: CoffeeShopDatabase, name: String = "Latte"): Long {
        val audit = AuditRepository(database, clock)
        val catalog = CatalogRepository(database, audit, clock)
        val orders = OrderRepository(database, InventoryRepository(database, audit, clock), audit, clock)
        val productId = catalog.saveProduct(ProductDraft(0, name, null, "", true, listOf(SizeInput(0, "Regular", 12_000)), emptySet()))
        val sizeId = catalog.getProductForEdit(productId)!!.sizes.single().id
        return orders.placeOrder(
            OrderDraft(
                listOf(CartLine(1, productId, sizeId, emptyMap(), 2)), Discount.None, "", "Table, 4",
                PaymentInput(PaymentMethod.CASH, cashReceivedCentavos = 50_000), completeNow = true
            )
        ).orderId
    }

    private fun count(database: CoffeeShopDatabase, table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun exportsOrdersCsvAndProfitPdf() = runBlocking<Unit> {
        sellLatte(db)
        val exports = ExportRepository(context, db, ReportRepository(db, clock), clock)

        val csvFile = File(dir, "orders.csv")
        val rows = exports.export(ExportType.ORDERS_CSV, DateRange.day(today), Uri.fromFile(csvFile))
        assertEquals(1, rows)
        val lines = csvFile.readText().removePrefix("﻿").split("\r\n")
        assertTrue(lines[0].startsWith("Order no.,Taken,Completed,Status"))
        assertTrue(lines[1].startsWith("261003-001,2026-10-03 10:00,2026-10-03 10:00,Completed,240.00,0.00,,240.00,0.00"))
        assertTrue(lines[1].endsWith("Cash,,\"Table, 4\""))

        val pdfFile = File(dir, "summary.pdf")
        exports.export(ExportType.PROFIT_SUMMARY_PDF, DateRange.day(today), Uri.fromFile(pdfFile))
        assertTrue(pdfFile.readBytes().copyOfRange(0, 4).decodeToString() == "%PDF")

        assertEquals("orders_2026-10-01_to_2026-10-03.csv", exports.suggestedFileName(ExportType.ORDERS_CSV, DateRange(today.minusDays(2), today)))
    }

    @Test
    fun backupThenRestoreBringsBackTheSavedData() = runBlocking<Unit> {
        sellLatte(db)
        val backupFile = File(dir, "backup.db")
        backups.backupTo(Uri.fromFile(backupFile))
        assertTrue(backups.lastBackupAt != null)

        // Data recorded after the backup...
        sellLatte(db, name = "Mocha")
        assertEquals(2, count(db, "orders"))

        // ...is what a restore throws away.
        val info = backups.inspect(Uri.fromFile(backupFile))
        assertEquals(1, info.orders)
        assertEquals(1, info.products)
        backups.scheduleRestore()
        assertTrue(backups.safetyCopy.exists())

        // What the app does on its next start: swap the file in before Room opens it.
        db.close()
        assertTrue(backups.applyPendingRestore())
        db = openDb()
        assertEquals(1, count(db, "orders"))
        assertEquals(1, count(db, "products"))
        assertFalse(backups.applyPendingRestore()) // nothing left to apply
        assertTrue(backups.lastRestoreAt != null)
    }

    @Test
    fun rejectsFilesThatAreNotBackups() = runBlocking<Unit> {
        val notABackup = File(dir, "notes.db").apply { writeText("hello, this is not a database") }
        assertThrows(UserFacingException::class.java) {
            runBlocking { backups.inspect(Uri.fromFile(notABackup)) }
        }
        // And nothing was staged, so a restart restores nothing.
        assertThrows(UserFacingException::class.java) { runBlocking { backups.scheduleRestore() } }
    }

    private companion object {
        const val DB_NAME = "data-export-backup-test.db"
    }
}
