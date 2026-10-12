package com.project2.coffeeshoppos.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.project2.coffeeshoppos.data.local.entity.CustomerEntity
import com.project2.coffeeshoppos.data.local.entity.LoyaltyEntryEntity
import com.project2.coffeeshoppos.data.local.entity.LoyaltySettingsEntity
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** A customer with their stamp count and visit history, for lists and the POS. */
data class CustomerSummary(
    @Embedded val customer: CustomerEntity,
    val stamps: Int,
    /** Completed orders. */
    val visits: Int,
    val totalSpentCentavos: Long,
    val lastVisitAt: Instant?,
)

private const val CUSTOMER_SUMMARY_COLUMNS = """
    SELECT c.*,
        (SELECT COALESCE(SUM(stampsChange), 0) FROM loyalty_entries WHERE customerId = c.id) AS stamps,
        (SELECT COUNT(*) FROM orders WHERE customerId = c.id AND status = 'COMPLETED') AS visits,
        (SELECT COALESCE(SUM(totalCentavos), 0) FROM orders WHERE customerId = c.id AND status = 'COMPLETED') AS totalSpentCentavos,
        (SELECT MAX(completedAt) FROM orders WHERE customerId = c.id AND status = 'COMPLETED') AS lastVisitAt
    FROM customers c
"""

/**
 * Stamps are only changed inside repository transactions that insert a loyalty entry,
 * which is why there is no "set stamps" query here.
 */
@Dao
interface LoyaltyDao {
    // ---------- Customers ----------

    @Insert
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomer(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phone = :phone")
    suspend fun getCustomerByPhone(phone: String): CustomerEntity?

    @Query("$CUSTOMER_SUMMARY_COLUMNS WHERE c.id = :id")
    fun observeCustomerSummary(id: Long): Flow<CustomerSummary?>

    /**
     * Customers whose name contains [query] or whose number contains [phoneDigits], best matches
     * first. Pass an empty [phoneDigits] to match on name only.
     */
    @Query(
        """$CUSTOMER_SUMMARY_COLUMNS
        WHERE c.isDeleted = 0
            AND (:query = '' OR c.name LIKE '%' || :query || '%' OR (:phoneDigits != '' AND c.phone LIKE '%' || :phoneDigits || '%'))
        ORDER BY lastVisitAt IS NULL, lastVisitAt DESC, c.name COLLATE NOCASE
        LIMIT :limit"""
    )
    fun searchCustomers(query: String, phoneDigits: String, limit: Int): Flow<List<CustomerSummary>>

    /** Different loyalty customers with an order completed between [from] and [to]. */
    @Query("SELECT COUNT(DISTINCT customerId) FROM orders WHERE customerId IS NOT NULL AND status = 'COMPLETED' AND completedAt >= :from AND completedAt < :to")
    fun observeCustomersServedBetween(from: Instant, to: Instant): Flow<Int>

    /** Every current customer, for the customers CSV. */
    @Query("$CUSTOMER_SUMMARY_COLUMNS WHERE c.isDeleted = 0 ORDER BY c.name COLLATE NOCASE")
    suspend fun getCustomersForExport(): List<CustomerSummary>

    /** What the customer orders most, by items bought in completed orders. */
    @Query(
        """SELECT l.productName FROM order_lines l JOIN orders o ON o.id = l.orderId
        WHERE o.customerId = :customerId AND o.status = 'COMPLETED'
        GROUP BY l.productName ORDER BY SUM(l.quantity) DESC, MAX(o.completedAt) DESC LIMIT 1"""
    )
    fun observeFavoriteProduct(customerId: Long): Flow<String?>

    @Query("SELECT * FROM orders WHERE customerId = :customerId ORDER BY createdAt DESC LIMIT :limit")
    fun observeCustomerOrders(customerId: Long, limit: Int): Flow<List<OrderEntity>>

    // ---------- Stamps ----------

    @Insert
    suspend fun insertEntry(entry: LoyaltyEntryEntity): Long

    @Query("SELECT COALESCE(SUM(stampsChange), 0) FROM loyalty_entries WHERE customerId = :customerId")
    suspend fun getStamps(customerId: Long): Int

    @Query("SELECT * FROM loyalty_entries WHERE orderId = :orderId ORDER BY id")
    suspend fun getEntriesForOrder(orderId: Long): List<LoyaltyEntryEntity>

    @Query("SELECT * FROM loyalty_entries WHERE orderId = :orderId ORDER BY id")
    fun observeEntriesForOrder(orderId: Long): Flow<List<LoyaltyEntryEntity>>

    @Query("SELECT * FROM loyalty_entries WHERE customerId = :customerId ORDER BY createdAt DESC, id DESC")
    fun observeEntries(customerId: Long): Flow<List<LoyaltyEntryEntity>>

    // ---------- Settings ----------

    @Query("SELECT * FROM loyalty_settings WHERE id = ${LoyaltySettingsEntity.SINGLE_ROW_ID}")
    fun observeSettings(): Flow<LoyaltySettingsEntity?>

    @Query("SELECT * FROM loyalty_settings WHERE id = ${LoyaltySettingsEntity.SINGLE_ROW_ID}")
    suspend fun getSettings(): LoyaltySettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: LoyaltySettingsEntity)
}
