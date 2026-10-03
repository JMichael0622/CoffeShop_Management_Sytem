package com.project2.coffeeshoppos.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import com.project2.coffeeshoppos.data.local.entity.RefundEntity
import com.project2.coffeeshoppos.domain.MovementType
import com.project2.coffeeshoppos.domain.PaymentMethod
import java.time.Instant
import kotlinx.coroutines.flow.Flow

data class MethodTotal(val method: PaymentMethod, val amountCentavos: Long)

data class ProductSales(val productName: String, val quantity: Int, val salesCentavos: Long, val cogsCentavos: Long)

data class MovementTotal(val type: MovementType, val costCentavos: Long)

/**
 * Read-only totals for reports. Every range is [from, to) in instants; the repository turns
 * calendar days in the shop's time zone into these. A sale belongs to the moment it was
 * completed (handed over), not when it was paid for.
 */
@Dao
interface ReportDao {
    /** Orders completed in the range, including ones refunded since. */
    @Query(
        "SELECT * FROM orders WHERE completedAt >= :from AND completedAt < :to " +
            "AND status IN ('COMPLETED', 'REFUNDED') ORDER BY completedAt"
    )
    fun observeCompletedOrders(from: Instant, to: Instant): Flow<List<OrderEntity>>

    /** Refunds of completed sales given in the range. Refunds from cancelled orders are not sales refunds. */
    @Query(
        """
        SELECT r.* FROM refunds r JOIN orders o ON o.id = r.orderId
        WHERE r.refundedAt >= :from AND r.refundedAt < :to AND o.status = 'REFUNDED'
        """
    )
    fun observeSaleRefunds(from: Instant, to: Instant): Flow<List<RefundEntity>>

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'CANCELLED' AND cancelledAt >= :from AND cancelledAt < :to")
    fun observeCancelledCount(from: Instant, to: Instant): Flow<Int>

    @Query(
        """
        SELECT p.method AS method, SUM(p.amountCentavos) AS amountCentavos
        FROM payments p JOIN orders o ON o.id = p.orderId
        WHERE o.completedAt >= :from AND o.completedAt < :to AND o.status IN ('COMPLETED', 'REFUNDED')
        GROUP BY p.method ORDER BY amountCentavos DESC
        """
    )
    fun observeSalesByMethod(from: Instant, to: Instant): Flow<List<MethodTotal>>

    /** Item sales by product name (as sold), refunded orders left out. Before order-level discounts. */
    @Query(
        """
        SELECT l.productName AS productName, SUM(l.quantity) AS quantity,
            SUM(l.lineTotalCentavos) AS salesCentavos, SUM(COALESCE(l.cogsCentavos, 0)) AS cogsCentavos
        FROM order_lines l JOIN orders o ON o.id = l.orderId
        WHERE o.completedAt >= :from AND o.completedAt < :to AND o.status = 'COMPLETED'
        GROUP BY l.productName ORDER BY salesCentavos DESC, quantity DESC
        """
    )
    fun observeProductSales(from: Instant, to: Instant): Flow<List<ProductSales>>

    @Query(
        """
        SELECT type, SUM(costCentavos) AS costCentavos FROM inventory_movements
        WHERE createdAt >= :from AND createdAt < :to AND type IN ('WASTAGE', 'ADJUSTMENT', 'REFUND_RETURN')
        GROUP BY type
        """
    )
    fun observeMovementTotals(from: Instant, to: Instant): Flow<List<MovementTotal>>

    @Query("SELECT COALESCE(SUM(totalCostCentavos), 0) FROM purchases WHERE purchasedAt >= :from AND purchasedAt < :to")
    fun observePurchasesTotal(from: Instant, to: Instant): Flow<Long>
}
