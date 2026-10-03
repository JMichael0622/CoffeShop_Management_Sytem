package com.project2.coffeeshoppos

import android.content.Context
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.repository.AuditRepository
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.data.repository.ExpenseRepository
import com.project2.coffeeshoppos.data.repository.InventoryRepository
import com.project2.coffeeshoppos.data.repository.OrderRepository
import java.time.Clock

/**
 * Creates the database and repositories once and shares them across the app.
 * This is "manual dependency injection": simpler and lighter to build than a DI library.
 */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()

    private val database: CoffeeShopDatabase by lazy { CoffeeShopDatabase.build(context) }

    val catalogRepository by lazy { CatalogRepository(database, auditRepository, clock) }
    val inventoryRepository by lazy { InventoryRepository(database, auditRepository, clock) }
    val orderRepository by lazy { OrderRepository(database, inventoryRepository, auditRepository, clock) }
    val expenseRepository by lazy { ExpenseRepository(database, auditRepository, clock) }
    val auditRepository by lazy { AuditRepository(database, clock) }
}
