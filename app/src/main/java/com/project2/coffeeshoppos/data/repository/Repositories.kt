package com.project2.coffeeshoppos.data.repository

import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.local.entity.AuditRecordEntity
import com.project2.coffeeshoppos.domain.AuditAction
import java.time.Clock
import kotlinx.coroutines.flow.Flow

// Repositories are the only place screens get data from. ViewModels never touch DAOs directly,
// so rules like "every stock change writes a movement" live in one place.
// Each repository grows in the phase that implements its feature (Catalog, Inventory, Order and Expense have their own files).

/** Names used in [AuditRecordEntity.entityType]. Never rename these; old records use them. */
object AuditEntityType {
    const val CATEGORY = "category"
    const val PRODUCT = "product"
    const val ADD_ON = "add_on"
    const val INGREDIENT = "ingredient"
    const val ORDER = "order"
    const val EXPENSE = "expense"
    const val RECURRING_EXPENSE = "recurring_expense"
    const val CUSTOMER = "customer"
    const val LOYALTY_SETTINGS = "loyalty_settings"
}

/**
 * Writes the audit trail. Call [record] inside the same database transaction as the change
 * it describes, so the change and its audit record are saved together or not at all.
 */
class AuditRepository(private val db: CoffeeShopDatabase, private val clock: Clock) {
    private val dao = db.auditDao()

    suspend fun record(
        entityType: String,
        entityId: Long,
        action: AuditAction,
        summary: String,
        details: String = "",
    ) {
        dao.insert(
            AuditRecordEntity(
                entityType = entityType,
                entityId = entityId,
                action = action,
                summary = summary,
                details = details,
                createdAt = clock.instant()
            )
        )
    }

    fun observeFor(entityType: String, entityId: Long): Flow<List<AuditRecordEntity>> =
        dao.observeFor(entityType, entityId)

    fun observeRecent(limit: Int = 50): Flow<List<AuditRecordEntity>> = dao.observeRecent(limit)
}
