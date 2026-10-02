package com.project2.coffeeshoppos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import com.project2.coffeeshoppos.data.local.entity.OrderLineAddOnEntity
import com.project2.coffeeshoppos.data.local.entity.OrderLineEntity
import com.project2.coffeeshoppos.data.local.entity.PaymentEntity
import com.project2.coffeeshoppos.data.local.entity.RefundEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrder(id: Long): OrderEntity?

    @Query("SELECT * FROM orders ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentOrders(limit: Int): Flow<List<OrderEntity>>

    @Query("SELECT COUNT(*) FROM orders")
    fun observeOrderCount(): Flow<Int>

    @Insert
    suspend fun insertLine(line: OrderLineEntity): Long

    @Query("SELECT * FROM order_lines WHERE orderId = :orderId ORDER BY id")
    suspend fun getLines(orderId: Long): List<OrderLineEntity>

    @Insert
    suspend fun insertLineAddOns(addOns: List<OrderLineAddOnEntity>)

    @Insert
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Insert
    suspend fun insertRefund(refund: RefundEntity): Long
}
