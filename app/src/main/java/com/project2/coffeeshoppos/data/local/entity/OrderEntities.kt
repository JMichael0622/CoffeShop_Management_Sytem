package com.project2.coffeeshoppos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.project2.coffeeshoppos.domain.OrderStatus
import com.project2.coffeeshoppos.domain.PaymentMethod
import java.time.Instant

// Orders copy ("snapshot") names, prices and costs at the time of sale, so editing the menu
// or buying stock at a new price never changes what an old order says.

@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["orderNumber"], unique = true),
        Index("status"),
        Index("createdAt"),
        Index("completedAt")
    ]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String,
    val status: OrderStatus,
    val subtotalCentavos: Long,
    val discountCentavos: Long = 0,
    val discountReason: String = "",
    val totalCentavos: Long,
    /** Cost of goods sold, fixed when the order is completed. Null before completion. */
    val cogsCentavos: Long? = null,
    val note: String = "",
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant? = null,
    val cancelledAt: Instant? = null,
)

@Entity(
    tableName = "order_lines",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ProductVariationEntity::class,
            parentColumns = ["id"],
            childColumns = ["variationId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("orderId"), Index("productId"), Index("variationId")]
)
data class OrderLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long?,
    val variationId: Long?,
    val productName: String,
    val variationName: String,
    val unitPriceCentavos: Long,
    val quantity: Int,
    /** Price of add-ons for ONE item; multiplied by quantity in lineTotalCentavos. */
    val addOnsUnitPriceCentavos: Long = 0,
    val lineTotalCentavos: Long,
    /** Ingredient cost of this whole line, fixed when the order is completed. */
    val cogsCentavos: Long? = null,
    val note: String = "",
)

@Entity(
    tableName = "order_line_add_ons",
    foreignKeys = [
        ForeignKey(
            entity = OrderLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderLineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AddOnEntity::class,
            parentColumns = ["id"],
            childColumns = ["addOnId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("orderLineId"), Index("addOnId")]
)
data class OrderLineAddOnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderLineId: Long,
    val addOnId: Long?,
    val addOnName: String,
    val priceCentavos: Long,
    /** How many of this add-on go on ONE item, e.g. 2 extra shots. */
    val quantityPerItem: Int = 1,
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("orderId"), Index("paidAt")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val method: PaymentMethod,
    val amountCentavos: Long,
    val cashReceivedCentavos: Long? = null,
    val changeCentavos: Long? = null,
    val reference: String = "",
    val paidAt: Instant,
)

/** A refund is its own record linked to the original order; the order itself is never rewritten. */
@Entity(
    tableName = "refunds",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("orderId"), Index("refundedAt")]
)
data class RefundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val amountCentavos: Long,
    val method: PaymentMethod,
    val reason: String,
    val reference: String = "",
    val refundedAt: Instant,
)
