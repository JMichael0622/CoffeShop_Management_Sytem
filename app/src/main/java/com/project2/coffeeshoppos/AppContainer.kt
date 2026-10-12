package com.project2.coffeeshoppos

import android.content.Context
import com.project2.coffeeshoppos.data.backup.BackupManager
import com.project2.coffeeshoppos.data.export.ExportRepository
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.repository.AuditRepository
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.data.repository.ExpenseRepository
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.data.repository.LoyaltyRepository
import com.project2.coffeeshoppos.data.repository.OrderRepository
import com.project2.coffeeshoppos.data.repository.ReportRepository
import java.time.Clock

/**
 * Creates the database and repositories once and shares them across the app.
 * This is "manual dependency injection": simpler and lighter to build than a DI library.
 */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()

    /** Created before the database so a restore picked last session is swapped in before Room opens it. */
    val backupManager = BackupManager(context, database = { database }, clock = clock)

    private val database: CoffeeShopDatabase by lazy {
        backupManager.applyPendingRestore()
        CoffeeShopDatabase.build(context)
    }

    val catalogRepository by lazy { CatalogRepository(database, auditRepository, clock) }
    val inventoryRepository by lazy { InventoryRepository(database, auditRepository, clock) }
    val orderRepository by lazy { OrderRepository(database, inventoryRepository, loyaltyRepository, auditRepository, clock) }
    val loyaltyRepository by lazy { LoyaltyRepository(database, auditRepository, clock) }
    val expenseRepository by lazy { ExpenseRepository(database, auditRepository, clock) }
    val reportRepository by lazy { ReportRepository(database, clock) }
    val auditRepository by lazy { AuditRepository(database, clock) }
    val exportRepository by lazy { ExportRepository(context, database, reportRepository, clock) }
}
