package com.project2.coffeeshoppos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.project2.coffeeshoppos.data.local.dao.AuditDao
import com.project2.coffeeshoppos.data.local.dao.CatalogDao
import com.project2.coffeeshoppos.data.local.dao.ExpenseDao
import com.project2.coffeeshoppos.data.local.dao.InventoryDao
import com.project2.coffeeshoppos.data.local.dao.OrderDao
import com.project2.coffeeshoppos.data.local.dao.ReportDao
import com.project2.coffeeshoppos.data.local.entity.AddOnEntity
import com.project2.coffeeshoppos.data.local.entity.AddOnRecipeItemEntity
import com.project2.coffeeshoppos.data.local.entity.AuditRecordEntity
import com.project2.coffeeshoppos.data.local.entity.CategoryEntity
import com.project2.coffeeshoppos.data.local.entity.ExpenseEntity
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.data.local.entity.InventoryMovementEntity
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import com.project2.coffeeshoppos.data.local.entity.OrderLineAddOnEntity
import com.project2.coffeeshoppos.data.local.entity.OrderLineEntity
import com.project2.coffeeshoppos.data.local.entity.PaymentEntity
import com.project2.coffeeshoppos.data.local.entity.ProductAddOnCrossRef
import com.project2.coffeeshoppos.data.local.entity.ProductEntity
import com.project2.coffeeshoppos.data.local.entity.ProductVariationEntity
import com.project2.coffeeshoppos.data.local.entity.PurchaseEntity
import com.project2.coffeeshoppos.data.local.entity.RecurringExpenseTemplateEntity
import com.project2.coffeeshoppos.data.local.entity.RefundEntity
import com.project2.coffeeshoppos.data.local.entity.VariationRecipeItemEntity

/**
 * The app's single SQLite database, stored privately on the phone.
 *
 * When a table changes: increase [version], add a [Migration] to [MIGRATIONS], and build.
 * Room then saves the new schema JSON in app/schemas/. Never use destructive migration here,
 * because that would erase the shop's records.
 */
@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        ProductVariationEntity::class,
        AddOnEntity::class,
        ProductAddOnCrossRef::class,
        VariationRecipeItemEntity::class,
        AddOnRecipeItemEntity::class,
        IngredientEntity::class,
        PurchaseEntity::class,
        InventoryMovementEntity::class,
        OrderEntity::class,
        OrderLineEntity::class,
        OrderLineAddOnEntity::class,
        PaymentEntity::class,
        RefundEntity::class,
        ExpenseEntity::class,
        RecurringExpenseTemplateEntity::class,
        AuditRecordEntity::class,
    ],
    version = CoffeeShopDatabase.VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CoffeeShopDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun orderDao(): OrderDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun auditDao(): AuditDao
    abstract fun reportDao(): ReportDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "coffee_shop.db"

        /** Upgrades from older database versions, added as the schema evolves. */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): CoffeeShopDatabase =
            Room.databaseBuilder(context, CoffeeShopDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
