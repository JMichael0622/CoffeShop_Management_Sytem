package com.project2.coffeeshoppos.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import com.project2.coffeeshoppos.data.local.entity.OrderLineAddOnEntity
import com.project2.coffeeshoppos.data.local.entity.OrderLineEntity
import com.project2.coffeeshoppos.data.local.entity.PaymentEntity
import com.project2.coffeeshoppos.data.local.entity.RefundEntity
import com.project2.coffeeshoppos.domain.PaymentMethod
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** One row in an order list: the order plus how many items it has and how it was paid. */
data class OrderListRow(
    @Embedded val order: OrderEntity,
    val itemCount: Int,
    val paymentMethod: PaymentMethod?,
)

private const val ORDER_LIST_COLUMNS = """
    SELECT o.*,
        (SELECT COALESCE(SUM(quantity), 0) FROM order_lines WHERE orderId = o.id) AS itemCount,
        (SELECT method FROM payments WHERE orderId = o.id ORDER BY id LIMIT 1) AS paymentMethod
    FROM orders o
"""

@Dao
interface OrderDao {
    @Insert
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrder(id: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE id = :id")
    fun observeOrder(id: Long): Flow<OrderEntity?>

    @Query("SELECT * FROM orders ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentOrders(limit: Int): Flow<List<OrderEntity>>

    @Query("SELECT COUNT(*) FROM orders")
    fun observeOrderCount(): Flow<Int>

    /** Orders taken in [from, to), newest first. */
    @Query("$ORDER_LIST_COLUMNS WHERE o.createdAt >= :from AND o.createdAt < :to ORDER BY o.createdAt DESC")
    fun observeOrdersBetween(from: Instant, to: Instant): Flow<List<OrderListRow>>

    /** Paid but not yet handed over, oldest first so the queue reads top to bottom. */
    @Query("$ORDER_LIST_COLUMNS WHERE o.status IN ('PENDING', 'PROCESSING') ORDER BY o.createdAt")
    fun observeActiveOrders(): Flow<List<OrderListRow>>

    @Query("SELECT COUNT(*) FROM orders WHERE createdAt >= :from AND createdAt < :to")
    suspend fun countOrdersBetween(from: Instant, to: Instant): Int

    @Insert
    suspend fun insertLine(line: OrderLineEntity): Long

    @Update
    suspend fun updateLines(lines: List<OrderLineEntity>)

    @Query("SELECT * FROM order_lines WHERE orderId = :orderId ORDER BY id")
    suspend fun getLines(orderId: Long): List<OrderLineEntity>

    @Query("SELECT * FROM order_lines WHERE orderId = :orderId ORDER BY id")
    fun observeLines(orderId: Long): Flow<List<OrderLineEntity>>

    @Insert
    suspend fun insertLineAddOns(addOns: List<OrderLineAddOnEntity>)

    @Query(
        """
        SELECT a.* FROM order_line_add_ons a JOIN order_lines l ON l.id = a.orderLineId
        WHERE l.orderId = :orderId ORDER BY a.id
        """
    )
    suspend fun getLineAddOns(orderId: Long): List<OrderLineAddOnEntity>

    @Query(
        """
        SELECT a.* FROM order_line_add_ons a JOIN order_lines l ON l.id = a.orderLineId
        WHERE l.orderId = :orderId ORDER BY a.id
        """
    )
    fun observeLineAddOns(orderId: Long): Flow<List<OrderLineAddOnEntity>>

    @Insert
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("SELECT * FROM payments WHERE orderId = :orderId ORDER BY id")
    suspend fun getPayments(orderId: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE orderId = :orderId ORDER BY id")
    fun observePayments(orderId: Long): Flow<List<PaymentEntity>>

    @Insert
    suspend fun insertRefund(refund: RefundEntity): Long

    @Query("SELECT * FROM refunds WHERE orderId = :orderId ORDER BY id")
    fun observeRefunds(orderId: Long): Flow<List<RefundEntity>>
}
