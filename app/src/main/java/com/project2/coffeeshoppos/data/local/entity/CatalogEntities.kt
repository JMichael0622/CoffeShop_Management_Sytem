package com.project2.coffeeshoppos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// Menu tables. Money is whole centavos (Long). Quantities are thousandths of the base unit (Long).
// Products, variations and add-ons are archived rather than deleted so old orders stay intact.

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Instant,
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId"), Index("name")]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long?,
    val name: String,
    val description: String = "",
    /** Temporarily hidden from the POS, e.g. sold out for the day. */
    val isAvailable: Boolean = true,
    /** Permanently retired from the menu. Kept for order history. */
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * A sellable version of a product with its own price and recipe, e.g. "Iced · 16 oz".
 * Every product has at least one variation (a single-size product gets one called "Regular").
 */
@Entity(
    tableName = "product_variations",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId")]
)
data class ProductVariationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val name: String,
    val priceCentavos: Long,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "add_ons",
    indices = [Index(value = ["name"], unique = true)]
)
data class AddOnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val priceCentavos: Long,
    val isAvailable: Boolean = true,
    val isArchived: Boolean = false,
    val createdAt: Instant,
)

/** Which add-ons may be offered with which products. */
@Entity(
    tableName = "product_add_ons",
    primaryKeys = ["productId", "addOnId"],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AddOnEntity::class,
            parentColumns = ["id"],
            childColumns = ["addOnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("addOnId")]
)
data class ProductAddOnCrossRef(
    val productId: Long,
    val addOnId: Long,
)

/** One ingredient consumed when one unit of a variation is sold, e.g. 18 g of coffee beans. */
@Entity(
    tableName = "variation_recipe_items",
    foreignKeys = [
        ForeignKey(
            entity = ProductVariationEntity::class,
            parentColumns = ["id"],
            childColumns = ["variationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["variationId", "ingredientId"], unique = true), Index("ingredientId")]
)
data class VariationRecipeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val variationId: Long,
    val ingredientId: Long,
    val quantityMilli: Long,
)

/** One ingredient consumed when one unit of an add-on is sold, e.g. 18 g for an extra shot. */
@Entity(
    tableName = "add_on_recipe_items",
    foreignKeys = [
        ForeignKey(
            entity = AddOnEntity::class,
            parentColumns = ["id"],
            childColumns = ["addOnId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["addOnId", "ingredientId"], unique = true), Index("ingredientId")]
)
data class AddOnRecipeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val addOnId: Long,
    val ingredientId: Long,
    val quantityMilli: Long,
)
