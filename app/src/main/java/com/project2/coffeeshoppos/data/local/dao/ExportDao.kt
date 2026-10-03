package com.project2.coffeeshoppos.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import com.project2.coffeeshoppos.data.local.entity.ExpenseEntity
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.data.local.entity.InventoryMovementEntity
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import com.project2.coffeeshoppos.data.local.entity.OrderLineEntity
import com.project2.coffeeshoppos.data.local.entity.PurchaseEntity
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.domain.OrderStatus
import com.project2.coffeeshoppos.domain.PaymentMethod
import java.time.Instant
import java.time.LocalDate

data class ExportOrderRow(
    @Embedded val order: OrderEntity,
    val paymentMethod: PaymentMethod?,
    val paymentReference: String?,
    val refundedCentavos: Long,
)

data class ExportLineRow(
    val orderNumber: String,
    val orderStatus: OrderStatus,
    val orderCreatedAt: Instant,
    @Embedded val line: OrderLineEntity,
    /** e.g. "2x Extra shot; Oat milk". */
    val addOns: String?,
)

data class ExportMovementRow(
    @Embedded val movement: InventoryMovementEntity,
    val ingredientName: String,
    val ingredientUnit: MeasureUnit,
)

data class ExportPurchaseRow(
    @Embedded val purchase: PurchaseEntity,
    val ingredientName: String,
)

/** One-off reads for saving data to files. Ranges are [from, to). */
@Dao
interface ExportDao {
    /** Every order taken in the range, whatever happened to it. */
    @Query(
        """
        SELECT o.*,
            (SELECT method FROM payments WHERE orderId = o.id ORDER BY id LIMIT 1) AS paymentMethod,
            (SELECT reference FROM payments WHERE orderId = o.id ORDER BY id LIMIT 1) AS paymentReference,
            (SELECT COALESCE(SUM(amountCentavos), 0) FROM refunds WHERE orderId = o.id) AS refundedCentavos
        FROM orders o WHERE o.createdAt >= :from AND o.createdAt < :to ORDER BY o.createdAt, o.id
        """
    )
    suspend fun getOrders(from: Instant, to: Instant): List<ExportOrderRow>

    @Query(
        """
        SELECT o.orderNumber AS orderNumber, o.status AS orderStatus, o.createdAt AS orderCreatedAt, l.*,
            (SELECT GROUP_CONCAT(CASE WHEN a.quantityPerItem > 1 THEN a.quantityPerItem || 'x ' || a.addOnName ELSE a.addOnName END, '; ')
             FROM order_line_add_ons a WHERE a.orderLineId = l.id) AS addOns
        FROM order_lines l JOIN orders o ON o.id = l.orderId
        WHERE o.createdAt >= :from AND o.createdAt < :to ORDER BY o.createdAt, o.id, l.id
        """
    )
    suspend fun getLines(from: Instant, to: Instant): List<ExportLineRow>

    @Query("SELECT * FROM expenses WHERE incurredDate BETWEEN :from AND :to ORDER BY incurredDate, id")
    suspend fun getExpenses(from: LocalDate, to: LocalDate): List<ExpenseEntity>

    @Query(
        """
        SELECT m.*, i.name AS ingredientName, i.baseUnit AS ingredientUnit
        FROM inventory_movements m JOIN ingredients i ON i.id = m.ingredientId
        WHERE m.createdAt >= :from AND m.createdAt < :to ORDER BY m.createdAt, m.id
        """
    )
    suspend fun getMovements(from: Instant, to: Instant): List<ExportMovementRow>

    @Query(
        """
        SELECT p.*, i.name AS ingredientName FROM purchases p JOIN ingredients i ON i.id = p.ingredientId
        WHERE p.purchasedAt >= :from AND p.purchasedAt < :to ORDER BY p.purchasedAt, p.id
        """
    )
    suspend fun getPurchases(from: Instant, to: Instant): List<ExportPurchaseRow>

    @Query("SELECT * FROM ingredients ORDER BY isArchived, name COLLATE NOCASE")
    suspend fun getIngredients(): List<IngredientEntity>
}
