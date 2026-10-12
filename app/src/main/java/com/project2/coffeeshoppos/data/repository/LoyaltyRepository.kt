package com.project2.coffeeshoppos.data.repository

import androidx.room.withTransaction
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.local.dao.CustomerSummary
import com.project2.coffeeshoppos.data.local.entity.CategoryEntity
import com.project2.coffeeshoppos.data.local.entity.CustomerEntity
import com.project2.coffeeshoppos.data.local.entity.LoyaltyEntryEntity
import com.project2.coffeeshoppos.data.local.entity.LoyaltySettingsEntity
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import com.project2.coffeeshoppos.domain.AuditAction
import com.project2.coffeeshoppos.domain.LoyaltyEntryType
import com.project2.coffeeshoppos.domain.LoyaltyMath
import com.project2.coffeeshoppos.domain.LoyaltyRules
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.util.formatPeso
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Customers and their loyalty stamps.
 *
 * Stamps follow the order: a free drink is taken off the card when the order is placed (so it
 * cannot be claimed twice), stamps are earned when the order is completed, and both are undone
 * if the order is cancelled or refunded. Every change writes one loyalty entry.
 */
class LoyaltyRepository(
    private val db: CoffeeShopDatabase,
    private val audit: AuditRepository,
    private val clock: Clock,
) {
    private val dao = db.loyaltyDao()
    private val catalogDao = db.catalogDao()

    // ---------- Rules ----------

    fun observeRules(): Flow<LoyaltyRules> = dao.observeSettings().map { it.toRules() }

    suspend fun getRules(): LoyaltyRules = dao.getSettings().toRules()

    suspend fun saveRules(rules: LoyaltyRules) = db.withTransaction {
        if (rules.stampsForReward !in LoyaltyRules.STAMPS_RANGE) {
            throw UserFacingException("Stamps for a free drink must be ${LoyaltyRules.STAMPS_RANGE.first} to ${LoyaltyRules.STAMPS_RANGE.last}")
        }
        if (rules.rewardCapCentavos !in LoyaltyRules.CAP_RANGE_CENTAVOS) {
            throw UserFacingException(
                "The free drink limit must be ${formatPeso(LoyaltyRules.CAP_RANGE_CENTAVOS.first)} to ${formatPeso(LoyaltyRules.CAP_RANGE_CENTAVOS.last)}"
            )
        }
        val previous = getRules()
        if (previous == rules) return@withTransaction
        dao.upsertSettings(
            LoyaltySettingsEntity(
                isEnabled = rules.isEnabled,
                stampsForReward = rules.stampsForReward,
                rewardCapCentavos = rules.rewardCapCentavos,
                updatedAt = clock.instant()
            )
        )
        audit.record(
            AuditEntityType.LOYALTY_SETTINGS, LoyaltySettingsEntity.SINGLE_ROW_ID, AuditAction.UPDATE,
            "Updated loyalty card rules",
            details = "Before: ${describe(previous)}\nAfter: ${describe(rules)}"
        )
    }

    /** Active categories, with whether each earns stamps. */
    fun observeCategories(): Flow<List<CategoryEntity>> = catalogDao.observeActiveCategories()

    /** Chooses whether items in a category earn stamps and can be the free drink. */
    suspend fun setCategoryEarnsStamps(categoryId: Long, earnsStamps: Boolean) = db.withTransaction {
        val category = catalogDao.getCategory(categoryId) ?: throw UserFacingException("Category not found")
        if (category.earnsStamps == earnsStamps) return@withTransaction
        catalogDao.updateCategory(category.copy(earnsStamps = earnsStamps))
        audit.record(
            AuditEntityType.CATEGORY, categoryId, AuditAction.UPDATE,
            "\"${category.name}\" ${if (earnsStamps) "now earns" else "no longer earns"} loyalty stamps"
        )
    }

    // ---------- Customers ----------

    /** Matches on name, or on mobile number when [query] contains digits. Empty shows recent customers. */
    fun searchCustomers(query: String, limit: Int = 30): Flow<List<CustomerSummary>> {
        val clean = query.trim()
        val digits = clean.filter { it.isDigit() }
        // "+63 917..." and "0917..." should find the same customer.
        val phoneDigits = when {
            digits.startsWith("63") && digits.length > 2 -> digits.drop(2)
            digits.startsWith("0") -> digits.drop(1)
            else -> digits
        }
        return dao.searchCustomers(clean, phoneDigits, limit)
    }

    fun observeCustomer(id: Long): Flow<CustomerSummary?> = dao.observeCustomerSummary(id)

    fun observeEntries(customerId: Long): Flow<List<LoyaltyEntryEntity>> = dao.observeEntries(customerId)

    /** Loyalty customers whose orders were handed over on [date], in the shop's time zone. */
    fun observeCustomersServedOn(date: LocalDate): Flow<Int> = dao.observeCustomersServedBetween(
        date.atStartOfDay(clock.zone).toInstant(),
        date.plusDays(1).atStartOfDay(clock.zone).toInstant()
    )

    fun observeFavoriteProduct(customerId: Long): Flow<String?> = dao.observeFavoriteProduct(customerId)

    fun observeCustomerOrders(customerId: Long, limit: Int = 20): Flow<List<OrderEntity>> =
        dao.observeCustomerOrders(customerId, limit)

    fun observeEntriesForOrder(orderId: Long): Flow<List<LoyaltyEntryEntity>> = dao.observeEntriesForOrder(orderId)

    /** Adds a customer and returns their id. Asks the customer first: their number is personal data. */
    suspend fun addCustomer(name: String, phone: String, note: String = ""): Long = db.withTransaction {
        val (cleanName, cleanPhone) = validate(name, phone, excludeId = 0)
        val now = clock.instant()
        val id = dao.insertCustomer(
            CustomerEntity(name = cleanName, phone = cleanPhone, note = note.trim(), createdAt = now, updatedAt = now)
        )
        audit.record(AuditEntityType.CUSTOMER, id, AuditAction.CREATE, "Added customer \"$cleanName\"")
        id
    }

    suspend fun updateCustomer(id: Long, name: String, phone: String, note: String) = db.withTransaction {
        val customer = activeCustomer(id)
        val (cleanName, cleanPhone) = validate(name, phone, excludeId = id)
        val updated = customer.copy(name = cleanName, phone = cleanPhone, note = note.trim(), updatedAt = clock.instant())
        if (updated.copy(updatedAt = customer.updatedAt) == customer) return@withTransaction
        dao.updateCustomer(updated)
        audit.record(AuditEntityType.CUSTOMER, id, AuditAction.UPDATE, "Updated customer \"$cleanName\"")
    }

    /**
     * Removes a customer's name, number and note for good. Their past orders and stamp history
     * stay so sales records still add up, but no longer say who the customer was.
     */
    suspend fun deleteCustomer(id: Long) = db.withTransaction {
        val customer = activeCustomer(id)
        dao.updateCustomer(
            customer.copy(name = DELETED_NAME, phone = null, note = "", isDeleted = true, updatedAt = clock.instant())
        )
        // The audit record must not keep the personal data either.
        audit.record(AuditEntityType.CUSTOMER, id, AuditAction.DELETE, "Deleted a customer's details")
    }

    /** Adds or removes stamps by hand, e.g. to carry over a paper card. */
    suspend fun adjustStamps(customerId: Long, change: Int, reason: String) = db.withTransaction {
        val customer = activeCustomer(customerId)
        if (change == 0) throw UserFacingException("Enter how many stamps to add or remove")
        if (change !in -MAX_ADJUSTMENT..MAX_ADJUSTMENT) throw UserFacingException("Adjust by at most $MAX_ADJUSTMENT stamps at a time")
        val cleanReason = reason.trim().ifEmpty { throw UserFacingException("Give a reason, e.g. carried over paper card") }
        val stamps = dao.getStamps(customerId)
        if (stamps + change < 0) throw UserFacingException("${customer.name} only has $stamps stamps")
        insertEntry(customerId, LoyaltyEntryType.ADJUSTMENT, change, cleanReason, orderId = null)
        audit.record(
            AuditEntityType.CUSTOMER, customerId, AuditAction.UPDATE,
            (if (change > 0) "Added $change" else "Removed ${-change}") + " stamps for \"${customer.name}\"",
            details = cleanReason
        )
    }

    // ---------- Called by OrderRepository, inside its transactions ----------

    /** Checks a customer can be attached to a new order. */
    internal suspend fun requireActiveCustomer(id: Long): CustomerEntity = activeCustomer(id)

    internal suspend fun getStamps(customerId: Long): Int = dao.getStamps(customerId)

    /**
     * Whether items of [productId] earn stamps and can be taken as the free drink. Products with
     * no category, or whose category was archived, earn stamps (the POS shows them the same way).
     */
    internal suspend fun earnsStamps(productId: Long?): Boolean {
        val categoryId = productId?.let { catalogDao.getProduct(it) }?.categoryId ?: return productId != null
        return catalogDao.getCategory(categoryId)?.takeIf { !it.isArchived }?.earnsStamps ?: true
    }

    /** Takes the stamps for a free drink off the card when the order is placed. */
    internal suspend fun redeem(customerId: Long, orderId: Long, rules: LoyaltyRules, itemName: String) {
        insertEntry(customerId, LoyaltyEntryType.REDEEMED, -rules.stampsForReward, "Free $itemName", orderId)
    }

    /** Adds the stamps an order earned when it is completed. */
    internal suspend fun earn(customerId: Long, orderId: Long, orderNumber: String, stamps: Int) {
        if (stamps <= 0) return
        insertEntry(customerId, LoyaltyEntryType.EARNED, stamps, "Order $orderNumber", orderId)
    }

    /**
     * Undoes an order's stamps when it is cancelled or refunded: earned stamps are taken back and
     * a free drink is given back. Safe to call more than once.
     */
    internal suspend fun reverse(orderId: Long, orderNumber: String) {
        val entries = dao.getEntriesForOrder(orderId)
        if (entries.isEmpty()) return
        val byCustomer = entries.groupBy { it.customerId }
        byCustomer.forEach { (customerId, rows) ->
            val earned = rows.filter { it.type == LoyaltyEntryType.EARNED || it.type == LoyaltyEntryType.EARNED_REVERSED }.sumOf { it.stampsChange }
            val redeemed = rows.filter { it.type == LoyaltyEntryType.REDEEMED || it.type == LoyaltyEntryType.REDEEM_RETURNED }.sumOf { it.stampsChange }
            if (earned != 0) insertEntry(customerId, LoyaltyEntryType.EARNED_REVERSED, -earned, "Order $orderNumber", orderId)
            if (redeemed != 0) insertEntry(customerId, LoyaltyEntryType.REDEEM_RETURNED, -redeemed, "Order $orderNumber", orderId)
        }
    }

    // ---------- Helpers ----------

    private suspend fun insertEntry(customerId: Long, type: LoyaltyEntryType, change: Int, reason: String, orderId: Long?) {
        dao.insertEntry(
            LoyaltyEntryEntity(
                customerId = customerId,
                type = type,
                stampsChange = change,
                reason = reason,
                orderId = orderId,
                createdAt = clock.instant()
            )
        )
    }

    private suspend fun activeCustomer(id: Long): CustomerEntity {
        val customer = dao.getCustomer(id)
        if (customer == null || customer.isDeleted) throw UserFacingException("Customer not found")
        return customer
    }

    private suspend fun validate(name: String, phone: String, excludeId: Long): Pair<String, String> {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        if (cleanName.isEmpty()) throw UserFacingException("Enter the customer's name")
        if (cleanName.length > MAX_NAME_LENGTH) throw UserFacingException("Name is too long")
        val cleanPhone = LoyaltyMath.normalizePhone(phone)
            ?: throw UserFacingException("Enter a mobile number like 0917 123 4567")
        dao.getCustomerByPhone(cleanPhone)?.takeIf { it.id != excludeId }?.let {
            throw UserFacingException("${LoyaltyMath.formatPhone(cleanPhone)} already belongs to ${it.name}")
        }
        return cleanName to cleanPhone
    }

    private fun LoyaltySettingsEntity?.toRules(): LoyaltyRules =
        this?.let { LoyaltyRules(it.isEnabled, it.stampsForReward, it.rewardCapCentavos) } ?: LoyaltyRules.DEFAULT

    private fun describe(rules: LoyaltyRules): String =
        (if (rules.isEnabled) "on" else "off") +
            ", ${rules.stampsForReward} stamps for a free drink up to ${formatPeso(rules.rewardCapCentavos)}"

    private companion object {
        const val DELETED_NAME = "Deleted customer"
        const val MAX_NAME_LENGTH = 60
        const val MAX_ADJUSTMENT = 100
    }
}
