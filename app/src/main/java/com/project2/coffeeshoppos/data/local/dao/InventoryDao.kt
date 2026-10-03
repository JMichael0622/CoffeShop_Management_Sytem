package com.project2.coffeeshoppos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.data.local.entity.InventoryMovementEntity
import com.project2.coffeeshoppos.data.local.entity.PurchaseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Stock levels are only changed inside repository transactions that also insert a movement.
 * That is why there is no general "set quantity" query here.
 */
@Dao
interface InventoryDao {
    @Query("SELECT * FROM ingredients WHERE isArchived = 0 ORDER BY name")
    fun observeActiveIngredients(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients ORDER BY isArchived, name COLLATE NOCASE")
    fun observeAllIngredients(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    fun observeIngredient(id: Long): Flow<IngredientEntity?>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    suspend fun getIngredient(id: Long): IngredientEntity?

    @Query("SELECT COUNT(*) FROM ingredients WHERE isArchived = 0")
    fun observeActiveIngredientCount(): Flow<Int>

    /** Case-insensitive name check, so "Milk" and "milk" count as the same. */
    @Query("SELECT COUNT(*) FROM ingredients WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countIngredientsNamed(name: String, excludeId: Long): Int

    @Insert
    suspend fun insertIngredient(ingredient: IngredientEntity): Long

    @Update
    suspend fun updateIngredient(ingredient: IngredientEntity)

    @Insert
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Query("SELECT * FROM purchases WHERE ingredientId = :ingredientId ORDER BY purchasedAt DESC")
    fun observePurchases(ingredientId: Long): Flow<List<PurchaseEntity>>

    @Insert
    suspend fun insertMovement(movement: InventoryMovementEntity): Long

    @Query("SELECT * FROM inventory_movements WHERE ingredientId = :ingredientId ORDER BY createdAt DESC, id DESC")
    fun observeMovements(ingredientId: Long): Flow<List<InventoryMovementEntity>>

    @Query("SELECT COUNT(*) FROM inventory_movements WHERE ingredientId = :ingredientId")
    suspend fun countMovements(ingredientId: Long): Int

    /** Names of the active sizes and add-ons whose recipes use an ingredient, e.g. "Iced Latte · 16 oz". */
    @Query(
        """
        SELECT p.name || ' · ' || v.name FROM variation_recipe_items r
        JOIN product_variations v ON v.id = r.variationId
        JOIN products p ON p.id = v.productId
        WHERE r.ingredientId = :ingredientId AND v.isArchived = 0 AND p.isArchived = 0
        UNION ALL
        SELECT a.name FROM add_on_recipe_items r
        JOIN add_ons a ON a.id = r.addOnId
        WHERE r.ingredientId = :ingredientId AND a.isArchived = 0
        """
    )
    fun observeActiveRecipeUsage(ingredientId: Long): Flow<List<String>>

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM variation_recipe_items r
                JOIN product_variations v ON v.id = r.variationId
                JOIN products p ON p.id = v.productId
                WHERE r.ingredientId = :ingredientId AND v.isArchived = 0 AND p.isArchived = 0)
             + (SELECT COUNT(*) FROM add_on_recipe_items r
                JOIN add_ons a ON a.id = r.addOnId
                WHERE r.ingredientId = :ingredientId AND a.isArchived = 0)
        """
    )
    suspend fun countActiveRecipeUses(ingredientId: Long): Int

    /** Every recipe line, archived products included, that points at an ingredient. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM variation_recipe_items WHERE ingredientId = :ingredientId)
             + (SELECT COUNT(*) FROM add_on_recipe_items WHERE ingredientId = :ingredientId)
        """
    )
    suspend fun countAllRecipeUses(ingredientId: Long): Int
}
