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

    @Query("SELECT * FROM ingredients WHERE id = :id")
    suspend fun getIngredient(id: Long): IngredientEntity?

    @Query("SELECT COUNT(*) FROM ingredients WHERE isArchived = 0")
    fun observeActiveIngredientCount(): Flow<Int>

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
}
