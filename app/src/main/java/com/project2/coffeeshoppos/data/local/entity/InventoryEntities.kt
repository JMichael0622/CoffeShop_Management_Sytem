package com.project2.coffeeshoppos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.domain.MovementType
import java.math.BigDecimal
import java.time.Instant

@Entity(
    tableName = "ingredients",
    indices = [Index(value = ["name"], unique = true)]
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "",
    /** Always a base unit: GRAM, MILLILITER or PIECE. */
    val baseUnit: MeasureUnit,
    /** Never edit directly; change only together with an inventory movement. */
    val quantityOnHandMilli: Long = 0,
    /** Weighted average cost in centavos per ONE base unit (e.g. per gram), kept to 6 decimals. */
    val averageCostCentavosPerUnit: BigDecimal = BigDecimal.ZERO,
    val reorderLevelMilli: Long = 0,
    val isArchived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** A stock purchase. This is a cash outflow, reported separately from operating expenses. */
@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("ingredientId"), Index("purchasedAt")]
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: Long,
    /** What the owner typed, e.g. 2.5 KILOGRAM, kept for display. */
    val enteredQuantity: BigDecimal,
    val enteredUnit: MeasureUnit,
    /** The same amount converted to the ingredient's base unit, in thousandths. */
    val quantityMilli: Long,
    val totalCostCentavos: Long,
    /** Centavos per ONE base unit for this purchase. */
    val unitCostCentavosPerUnit: BigDecimal,
    val supplier: String = "",
    val reference: String = "",
    val notes: String = "",
    val purchasedAt: Instant,
    val createdAt: Instant,
)

/**
 * The stock ledger. Every change to an ingredient's quantity on hand writes exactly one row here,
 * so the history always explains the current stock level.
 */
@Entity(
    tableName = "inventory_movements",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("ingredientId"), Index("orderId"), Index("purchaseId"), Index("createdAt")]
)
data class InventoryMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientId: Long,
    val type: MovementType,
    /** Positive adds stock, negative removes it. */
    val quantityChangeMilli: Long,
    val quantityAfterMilli: Long,
    /** Average cost at the moment of the movement, so history never gets recalculated. */
    val unitCostCentavosPerUnit: BigDecimal,
    /** Value of this movement in centavos (negative for stock leaving). */
    val costCentavos: Long,
    val reason: String,
    val orderId: Long? = null,
    val purchaseId: Long? = null,
    val createdAt: Instant,
)
