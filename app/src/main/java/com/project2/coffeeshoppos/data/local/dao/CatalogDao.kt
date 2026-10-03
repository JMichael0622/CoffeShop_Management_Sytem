package com.project2.coffeeshoppos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.project2.coffeeshoppos.data.local.entity.AddOnEntity
import com.project2.coffeeshoppos.data.local.entity.AddOnRecipeItemEntity
import com.project2.coffeeshoppos.data.local.entity.CategoryEntity
import com.project2.coffeeshoppos.data.local.entity.ProductAddOnCrossRef
import com.project2.coffeeshoppos.data.local.entity.ProductEntity
import com.project2.coffeeshoppos.data.local.entity.ProductVariationEntity
import com.project2.coffeeshoppos.data.local.entity.VariationRecipeItemEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    // Categories
    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder, name")
    fun observeActiveCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isArchived, sortOrder, name")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder, name")
    suspend fun getActiveCategories(): List<CategoryEntity>

    /** Case-insensitive name check, so "Coffee" and "coffee" count as the same. */
    @Query("SELECT COUNT(*) FROM categories WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countCategoriesNamed(name: String, excludeId: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    suspend fun nextCategorySortOrder(): Int

    @Query("SELECT COUNT(*) FROM products WHERE categoryId = :categoryId AND isArchived = 0")
    suspend fun countActiveProductsInCategory(categoryId: Long): Int

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Update
    suspend fun updateCategories(categories: List<CategoryEntity>)

    // Products
    @Query("SELECT * FROM products WHERE isArchived = 0 ORDER BY sortOrder, name")
    fun observeActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name")
    fun observeAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProduct(id: Long): ProductEntity?

    @Query("SELECT COUNT(*) FROM products WHERE isArchived = 0")
    fun observeActiveProductCount(): Flow<Int>

    @Insert
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET isAvailable = :available, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setProductAvailable(id: Long, available: Boolean, updatedAt: Instant)

    // Variations (shown to the owner as "sizes")
    @Query("SELECT * FROM product_variations WHERE productId = :productId ORDER BY sortOrder, id")
    fun observeVariations(productId: Long): Flow<List<ProductVariationEntity>>

    @Query("SELECT * FROM product_variations WHERE productId = :productId ORDER BY sortOrder, id")
    suspend fun getVariations(productId: Long): List<ProductVariationEntity>

    @Query("SELECT * FROM product_variations WHERE isArchived = 0")
    fun observeAllActiveVariations(): Flow<List<ProductVariationEntity>>

    @Insert
    suspend fun insertVariation(variation: ProductVariationEntity): Long

    @Update
    suspend fun updateVariation(variation: ProductVariationEntity)

    // Add-ons
    @Query("SELECT * FROM add_ons WHERE isArchived = 0 ORDER BY name")
    fun observeActiveAddOns(): Flow<List<AddOnEntity>>

    @Query("SELECT * FROM add_ons ORDER BY isArchived, name")
    fun observeAllAddOns(): Flow<List<AddOnEntity>>

    @Query("SELECT * FROM add_ons WHERE id = :id")
    suspend fun getAddOn(id: Long): AddOnEntity?

    @Query("SELECT * FROM add_ons ORDER BY name")
    suspend fun getAllAddOns(): List<AddOnEntity>

    @Query("SELECT COUNT(*) FROM add_ons WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countAddOnsNamed(name: String, excludeId: Long): Int

    @Insert
    suspend fun insertAddOn(addOn: AddOnEntity): Long

    @Update
    suspend fun updateAddOn(addOn: AddOnEntity)

    @Query("SELECT addOnId FROM product_add_ons WHERE productId = :productId")
    suspend fun getProductAddOnIds(productId: Long): List<Long>

    @Insert
    suspend fun insertProductAddOns(links: List<ProductAddOnCrossRef>)

    @Query("DELETE FROM product_add_ons WHERE productId = :productId")
    suspend fun deleteProductAddOns(productId: Long)

    @Query("SELECT * FROM product_variations WHERE id = :id")
    suspend fun getVariation(id: Long): ProductVariationEntity?

    // Recipes
    @Query("SELECT * FROM variation_recipe_items")
    fun observeAllVariationRecipeItems(): Flow<List<VariationRecipeItemEntity>>

    @Query("SELECT * FROM add_on_recipe_items")
    fun observeAllAddOnRecipeItems(): Flow<List<AddOnRecipeItemEntity>>

    @Query("SELECT * FROM variation_recipe_items WHERE variationId = :variationId ORDER BY id")
    suspend fun getVariationRecipe(variationId: Long): List<VariationRecipeItemEntity>

    @Insert
    suspend fun insertVariationRecipeItems(items: List<VariationRecipeItemEntity>)

    @Query("DELETE FROM variation_recipe_items WHERE variationId = :variationId")
    suspend fun deleteVariationRecipe(variationId: Long)

    @Query("SELECT * FROM add_on_recipe_items WHERE addOnId = :addOnId ORDER BY id")
    suspend fun getAddOnRecipe(addOnId: Long): List<AddOnRecipeItemEntity>

    @Insert
    suspend fun insertAddOnRecipeItems(items: List<AddOnRecipeItemEntity>)

    @Query("DELETE FROM add_on_recipe_items WHERE addOnId = :addOnId")
    suspend fun deleteAddOnRecipe(addOnId: Long)
}
