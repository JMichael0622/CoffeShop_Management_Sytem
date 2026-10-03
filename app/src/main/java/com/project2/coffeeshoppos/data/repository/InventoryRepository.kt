package com.project2.coffeeshoppos.data.repository

import androidx.room.withTransaction
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.local.entity.AddOnRecipeItemEntity
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.data.local.entity.InventoryMovementEntity
import com.project2.coffeeshoppos.data.local.entity.ProductEntity
import com.project2.coffeeshoppos.data.local.entity.PurchaseEntity
import com.project2.coffeeshoppos.data.local.entity.VariationRecipeItemEntity
import com.project2.coffeeshoppos.domain.AuditAction
import com.project2.coffeeshoppos.domain.IngredientDraft
import com.project2.coffeeshoppos.domain.InventoryMath
import com.project2.coffeeshoppos.domain.MovementType
import com.project2.coffeeshoppos.domain.PurchaseDraft
import com.project2.coffeeshoppos.domain.RecipeLine
import com.project2.coffeeshoppos.domain.RecipeTarget
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.domain.formatQuantity
import com.project2.coffeeshoppos.domain.recipeCostCentavos
import com.project2.coffeeshoppos.util.formatPeso
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One size or add-on in the recipes list, with what its ingredients cost today. */
data class RecipeCostRow(
    val target: RecipeTarget,
    val name: String,
    val priceCentavos: Long,
    val lineCount: Int,
    val costCentavos: Long,
    /** True when an ingredient in the recipe has no cost yet, so [costCentavos] is too low. */
    val hasUncostedIngredient: Boolean,
)

data class ProductRecipes(
    val productId: Long,
    val productName: String,
    val categoryName: String?,
    val sizes: List<RecipeCostRow>,
)

data class RecipeOverview(val products: List<ProductRecipes>, val addOns: List<RecipeCostRow>)

/** A recipe as loaded into the editor. */
data class RecipeEditData(
    /** e.g. "Iced Latte · 16 oz" or "Extra shot". */
    val title: String,
    val priceCentavos: Long,
    val lines: List<RecipeLine>,
)

/**
 * Ingredients, purchases, stock movements and recipes.
 *
 * The one rule this class exists to keep: an ingredient's quantity on hand and average cost
 * only ever change together with a new row in the stock ledger (inventory_movements), inside
 * one transaction. Adding up an ingredient's movements always gives its current stock.
 */
class InventoryRepository(
    private val db: CoffeeShopDatabase,
    private val audit: AuditRepository,
    private val clock: Clock,
) {
    private val dao = db.inventoryDao()
    private val catalogDao = db.catalogDao()

    // ---------- Reading ----------

    fun observeIngredients(): Flow<List<IngredientEntity>> = dao.observeActiveIngredients()
    fun observeAllIngredients(): Flow<List<IngredientEntity>> = dao.observeAllIngredients()
    fun observeIngredientCount(): Flow<Int> = dao.observeActiveIngredientCount()
    fun observeIngredient(id: Long): Flow<IngredientEntity?> = dao.observeIngredient(id)
    fun observeMovements(ingredientId: Long): Flow<List<InventoryMovementEntity>> = dao.observeMovements(ingredientId)
    fun observeRecipeUsage(ingredientId: Long): Flow<List<String>> = dao.observeActiveRecipeUsage(ingredientId)

    suspend fun getIngredient(id: Long): IngredientEntity? = dao.getIngredient(id)

    /** The unit can only change while nothing has been recorded in it yet. */
    suspend fun canChangeUnit(ingredientId: Long): Boolean =
        dao.countMovements(ingredientId) == 0 && dao.countAllRecipeUses(ingredientId) == 0

    // ---------- Ingredients ----------

    /** Creates an ingredient, recording any opening stock as its first movement. Returns its id. */
    suspend fun addIngredient(draft: IngredientDraft): Long = db.withTransaction {
        val now = clock.instant()
        val name = cleanName(draft.name)
        checkNameFree(name, excludeId = 0)
        if (!draft.baseUnit.isBaseUnit) throw UserFacingException("Store ingredients in g, ml or pc")
        if (draft.reorderLevelMilli < 0) throw UserFacingException("Reorder level cannot be negative")
        if (draft.openingQuantityMilli < 0) throw UserFacingException("Opening stock cannot be negative")
        if (draft.openingTotalCostCentavos < 0) throw UserFacingException("Cost cannot be negative")

        val id = dao.insertIngredient(
            IngredientEntity(
                name = name,
                category = draft.category.trim(),
                baseUnit = draft.baseUnit,
                reorderLevelMilli = draft.reorderLevelMilli,
                createdAt = now,
                updatedAt = now
            )
        )
        if (draft.openingQuantityMilli > 0) {
            val ingredient = requireNotNull(dao.getIngredient(id))
            val unitCost = InventoryMath.unitCost(draft.openingTotalCostCentavos, draft.openingQuantityMilli)
            applyMovement(
                ingredient = ingredient,
                type = MovementType.OPENING_BALANCE,
                changeMilli = draft.openingQuantityMilli,
                costCentavos = draft.openingTotalCostCentavos,
                newAverageCost = unitCost,
                reason = "Opening stock"
            )
        }
        audit.record(
            AuditEntityType.INGREDIENT, id, AuditAction.CREATE, "Created ingredient \"$name\"",
            details = if (draft.openingQuantityMilli > 0) {
                "Opening stock: ${formatQuantity(draft.openingQuantityMilli, draft.baseUnit)} " +
                    "for ${formatPeso(draft.openingTotalCostCentavos)}"
            } else ""
        )
        id
    }

    /** Edits an ingredient's details. Stock is never changed here; use a movement instead. */
    suspend fun updateIngredient(draft: IngredientDraft) = db.withTransaction {
        val current = dao.getIngredient(draft.id) ?: throw UserFacingException("Ingredient not found")
        val name = cleanName(draft.name)
        checkNameFree(name, excludeId = current.id)
        if (draft.reorderLevelMilli < 0) throw UserFacingException("Reorder level cannot be negative")
        if (draft.baseUnit != current.baseUnit) {
            if (!draft.baseUnit.isBaseUnit) throw UserFacingException("Store ingredients in g, ml or pc")
            if (!canChangeUnit(current.id)) {
                throw UserFacingException("The unit cannot change once stock or recipes use it")
            }
        }
        val updated = current.copy(
            name = name,
            category = draft.category.trim(),
            baseUnit = draft.baseUnit,
            reorderLevelMilli = draft.reorderLevelMilli,
            updatedAt = clock.instant()
        )
        if (updated == current.copy(updatedAt = updated.updatedAt)) return@withTransaction
        dao.updateIngredient(updated)

        val changes = buildList {
            if (current.name != name) add("Name: \"${current.name}\" → \"$name\"")
            if (current.category != updated.category) add("Category: \"${current.category}\" → \"${updated.category}\"")
            if (current.baseUnit != updated.baseUnit) add("Unit: ${current.baseUnit.symbol} → ${updated.baseUnit.symbol}")
            if (current.reorderLevelMilli != updated.reorderLevelMilli) {
                add(
                    "Reorder level: ${formatQuantity(current.reorderLevelMilli, current.baseUnit)} → " +
                        formatQuantity(updated.reorderLevelMilli, updated.baseUnit)
                )
            }
        }
        audit.record(
            AuditEntityType.INGREDIENT, current.id, AuditAction.UPDATE, "Updated ingredient \"$name\"",
            details = changes.joinToString("\n")
        )
    }

    suspend fun setIngredientArchived(id: Long, archived: Boolean) = db.withTransaction {
        val ingredient = dao.getIngredient(id) ?: throw UserFacingException("Ingredient not found")
        if (archived) {
            val uses = dao.countActiveRecipeUses(id)
            if (uses > 0) {
                throw UserFacingException(
                    "\"${ingredient.name}\" is still in $uses " + (if (uses == 1) "recipe" else "recipes") +
                        ". Remove it from those recipes first."
                )
            }
        }
        dao.updateIngredient(ingredient.copy(isArchived = archived, updatedAt = clock.instant()))
        audit.record(
            AuditEntityType.INGREDIENT, id,
            if (archived) AuditAction.ARCHIVE else AuditAction.RESTORE,
            "${if (archived) "Archived" else "Restored"} ingredient \"${ingredient.name}\""
        )
    }

    // ---------- Stock movements ----------

    /**
     * Records a stock purchase: saves the purchase, adds the stock, and recalculates the
     * weighted average cost. Returns the purchase id.
     */
    suspend fun recordPurchase(draft: PurchaseDraft): Long = db.withTransaction {
        val ingredient = activeIngredient(draft.ingredientId)
        val quantityMilli = draft.enteredUnit.toMilli(draft.enteredQuantity, ingredient.baseUnit)
        if (quantityMilli <= 0) throw UserFacingException("Enter how much you bought")
        if (draft.totalCostCentavos < 0) throw UserFacingException("Cost cannot be negative")
        val today = LocalDate.now(clock)
        if (draft.purchasedOn.isAfter(today)) throw UserFacingException("The purchase date cannot be in the future")

        val now = clock.instant()
        // A purchase dated today keeps the exact time; an earlier one is placed at noon of that day.
        val purchasedAt = if (draft.purchasedOn == today) now
        else draft.purchasedOn.atTime(LocalTime.NOON).atZone(clock.zone).toInstant()
        val supplier = draft.supplier.trim()
        val reference = draft.reference.trim()

        val purchaseId = dao.insertPurchase(
            PurchaseEntity(
                ingredientId = ingredient.id,
                enteredQuantity = draft.enteredQuantity,
                enteredUnit = draft.enteredUnit,
                quantityMilli = quantityMilli,
                totalCostCentavos = draft.totalCostCentavos,
                unitCostCentavosPerUnit = InventoryMath.unitCost(draft.totalCostCentavos, quantityMilli),
                supplier = supplier,
                reference = reference,
                notes = draft.notes.trim(),
                purchasedAt = purchasedAt,
                createdAt = now
            )
        )
        applyMovement(
            ingredient = ingredient,
            type = MovementType.PURCHASE,
            changeMilli = quantityMilli,
            costCentavos = draft.totalCostCentavos,
            newAverageCost = InventoryMath.weightedAverage(
                ingredient.quantityOnHandMilli,
                ingredient.averageCostCentavosPerUnit,
                quantityMilli,
                draft.totalCostCentavos
            ),
            reason = listOf(
                if (supplier.isEmpty()) "Purchase" else "Bought from $supplier",
                reference.takeIf { it.isNotEmpty() }?.let { "Ref $it" }
            ).filterNotNull().joinToString(" · "),
            purchaseId = purchaseId
        )
        purchaseId
    }

    /** Removes spoiled, spilled or expired stock at its average cost. */
    suspend fun recordWastage(ingredientId: Long, quantityMilli: Long, reason: String) = db.withTransaction {
        val ingredient = activeIngredient(ingredientId)
        if (quantityMilli <= 0) throw UserFacingException("Enter how much was wasted")
        if (quantityMilli > ingredient.quantityOnHandMilli) {
            throw UserFacingException(
                "Only ${formatQuantity(ingredient.quantityOnHandMilli.coerceAtLeast(0), ingredient.baseUnit)} " +
                    "is on hand. Use a stock count if the recorded stock is wrong."
            )
        }
        val cleanReason = reason.trim().ifEmpty { throw UserFacingException("Say what happened, e.g. spoiled milk") }
        applyMovement(
            ingredient = ingredient,
            type = MovementType.WASTAGE,
            changeMilli = -quantityMilli,
            costCentavos = -InventoryMath.costOf(quantityMilli, ingredient.averageCostCentavosPerUnit),
            newAverageCost = ingredient.averageCostCentavosPerUnit,
            reason = cleanReason
        )
    }

    /**
     * Sets the stock to what was physically counted, recording the difference as an adjustment.
     * Returns the change in milli (0 if the count already matched, in which case nothing is saved).
     */
    suspend fun recordStockCount(ingredientId: Long, countedMilli: Long, note: String): Long = db.withTransaction {
        val ingredient = activeIngredient(ingredientId)
        if (countedMilli < 0) throw UserFacingException("The count cannot be negative")
        val change = countedMilli - ingredient.quantityOnHandMilli
        if (change == 0L) return@withTransaction 0L
        val cost = InventoryMath.costOf(kotlin.math.abs(change), ingredient.averageCostCentavosPerUnit)
        applyMovement(
            ingredient = ingredient,
            type = MovementType.ADJUSTMENT,
            changeMilli = change,
            costCentavos = if (change > 0) cost else -cost,
            newAverageCost = ingredient.averageCostCentavosPerUnit,
            reason = note.trim().ifEmpty { "Stock count" }
        )
        change
    }

    /**
     * Takes the ingredients used by a completed order out of stock, one movement per ingredient,
     * at average cost. Stock may go below zero: a sale is never blocked because the stock
     * records are behind. Must run inside the order's transaction. Returns the total cost.
     */
    internal suspend fun recordSale(orderId: Long, orderNumber: String, usageMilli: Map<Long, Long>): Long {
        var total = 0L
        usageMilli.filterValues { it > 0 }.toSortedMap().forEach { (ingredientId, milli) ->
            val ingredient = dao.getIngredient(ingredientId) ?: return@forEach
            val cost = InventoryMath.costOf(milli, ingredient.averageCostCentavosPerUnit)
            applyMovement(
                ingredient = ingredient,
                type = MovementType.SALE,
                changeMilli = -milli,
                costCentavos = -cost,
                newAverageCost = ingredient.averageCostCentavosPerUnit,
                reason = "Order $orderNumber",
                orderId = orderId
            )
            total += cost
        }
        return total
    }

    /**
     * Puts back what a refunded order took out, at the cost it was taken out at.
     * Must run inside the refund's transaction. Returns the value returned to stock.
     */
    internal suspend fun returnSale(orderId: Long, orderNumber: String): Long {
        var total = 0L
        dao.getOrderMovements(orderId, MovementType.SALE).forEach { sale ->
            val ingredient = dao.getIngredient(sale.ingredientId) ?: return@forEach
            val milli = -sale.quantityChangeMilli
            val cost = -sale.costCentavos
            if (milli <= 0) return@forEach
            applyMovement(
                ingredient = ingredient,
                type = MovementType.REFUND_RETURN,
                changeMilli = milli,
                costCentavos = cost,
                newAverageCost = InventoryMath.weightedAverage(
                    ingredient.quantityOnHandMilli, ingredient.averageCostCentavosPerUnit, milli, cost
                ),
                reason = "Refund of order $orderNumber",
                orderId = orderId
            )
            total += cost
        }
        return total
    }

    /** The only place quantity on hand changes: updates the ingredient and writes its ledger row. */
    private suspend fun applyMovement(
        ingredient: IngredientEntity,
        type: MovementType,
        changeMilli: Long,
        costCentavos: Long,
        newAverageCost: BigDecimal,
        reason: String,
        purchaseId: Long? = null,
        orderId: Long? = null,
    ) {
        val now = clock.instant()
        val after = ingredient.quantityOnHandMilli + changeMilli
        dao.updateIngredient(
            ingredient.copy(quantityOnHandMilli = after, averageCostCentavosPerUnit = newAverageCost, updatedAt = now)
        )
        dao.insertMovement(
            InventoryMovementEntity(
                ingredientId = ingredient.id,
                type = type,
                quantityChangeMilli = changeMilli,
                quantityAfterMilli = after,
                unitCostCentavosPerUnit = newAverageCost,
                costCentavos = costCentavos,
                reason = reason,
                purchaseId = purchaseId,
                orderId = orderId,
                createdAt = now
            )
        )
    }

    // ---------- Recipes ----------

    /** Every active product size and add-on with its recipe cost at today's average costs. */
    fun observeRecipeOverview(): Flow<RecipeOverview> = combine(
        combine(catalogDao.observeActiveProducts(), catalogDao.observeAllCategories(), catalogDao.observeAllActiveVariations(), ::Triple),
        catalogDao.observeActiveAddOns(),
        catalogDao.observeAllVariationRecipeItems(),
        catalogDao.observeAllAddOnRecipeItems(),
        dao.observeAllIngredients(),
    ) { (products, categories, sizes), addOns, sizeRecipes, addOnRecipes, ingredients ->
        val ingredientsById = ingredients.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        val categoryOrder = categories.withIndex().associate { (index, category) -> category.id to index }
        val sizeLines = sizeRecipes.groupBy({ it.variationId }, { RecipeLine(it.ingredientId, it.quantityMilli) })
        val addOnLines = addOnRecipes.groupBy({ it.addOnId }, { RecipeLine(it.ingredientId, it.quantityMilli) })
        val sizesByProduct = sizes.groupBy { it.productId }

        fun row(target: RecipeTarget, name: String, price: Long, lines: List<RecipeLine>) = RecipeCostRow(
            target = target,
            name = name,
            priceCentavos = price,
            lineCount = lines.size,
            costCentavos = recipeCostCentavos(lines, ingredientsById),
            hasUncostedIngredient = lines.any {
                (ingredientsById[it.ingredientId]?.averageCostCentavosPerUnit ?: BigDecimal.ZERO).signum() == 0
            }
        )

        RecipeOverview(
            products = products
                .sortedWith(
                    compareBy<ProductEntity> {
                        categoryOrder[it.categoryId] ?: Int.MAX_VALUE
                    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                )
                .map { product ->
                    ProductRecipes(
                        productId = product.id,
                        productName = product.name,
                        categoryName = product.categoryId?.let { categoriesById[it]?.name },
                        sizes = sizesByProduct[product.id].orEmpty()
                            .sortedBy { it.sortOrder }
                            .map { size ->
                                row(RecipeTarget.Variation(size.id), size.name, size.priceCentavos, sizeLines[size.id].orEmpty())
                            }
                    )
                },
            addOns = addOns.map { addOn ->
                row(RecipeTarget.AddOn(addOn.id), addOn.name, addOn.priceCentavos, addOnLines[addOn.id].orEmpty())
            }
        )
    }

    suspend fun getRecipeForEdit(target: RecipeTarget): RecipeEditData? = when (target) {
        is RecipeTarget.Variation -> {
            val size = catalogDao.getVariation(target.variationId)
            val product = size?.let { catalogDao.getProduct(it.productId) }
            if (size == null || product == null) null else RecipeEditData(
                title = "${product.name} · ${size.name}",
                priceCentavos = size.priceCentavos,
                lines = catalogDao.getVariationRecipe(size.id).map { RecipeLine(it.ingredientId, it.quantityMilli) }
            )
        }
        is RecipeTarget.AddOn -> catalogDao.getAddOn(target.addOnId)?.let { addOn ->
            RecipeEditData(
                title = addOn.name,
                priceCentavos = addOn.priceCentavos,
                lines = catalogDao.getAddOnRecipe(addOn.id).map { RecipeLine(it.ingredientId, it.quantityMilli) }
            )
        }
    }

    /** Replaces a recipe with [lines]. Each ingredient may appear once, with a positive quantity. */
    suspend fun saveRecipe(target: RecipeTarget, lines: List<RecipeLine>) = db.withTransaction {
        if (lines.any { it.quantityMilli <= 0 }) throw UserFacingException("Every ingredient needs a quantity above zero")
        if (lines.map { it.ingredientId }.toSet().size != lines.size) {
            throw UserFacingException("Each ingredient can only be listed once")
        }
        val previous = when (target) {
            is RecipeTarget.Variation -> catalogDao.getVariationRecipe(target.variationId).map { RecipeLine(it.ingredientId, it.quantityMilli) }
            is RecipeTarget.AddOn -> catalogDao.getAddOnRecipe(target.addOnId).map { RecipeLine(it.ingredientId, it.quantityMilli) }
        }
        val ingredients = lines.associate { line ->
            val ingredient = dao.getIngredient(line.ingredientId) ?: throw UserFacingException("Ingredient not found")
            // An archived ingredient may stay in a recipe that already had it, but cannot be newly added.
            if (ingredient.isArchived && previous.none { it.ingredientId == ingredient.id }) {
                throw UserFacingException("\"${ingredient.name}\" is archived. Restore it before adding it to a recipe.")
            }
            ingredient.id to ingredient
        }
        if (previous.toSet() == lines.toSet()) return@withTransaction

        val description = lines.joinToString("\n") { line ->
            val ingredient = ingredients.getValue(line.ingredientId)
            "${ingredient.name}: ${formatQuantity(line.quantityMilli, ingredient.baseUnit)}"
        }.ifEmpty { "No ingredients" }

        when (target) {
            is RecipeTarget.Variation -> {
                val size = catalogDao.getVariation(target.variationId) ?: throw UserFacingException("Size not found")
                catalogDao.deleteVariationRecipe(size.id)
                catalogDao.insertVariationRecipeItems(
                    lines.map { VariationRecipeItemEntity(variationId = size.id, ingredientId = it.ingredientId, quantityMilli = it.quantityMilli) }
                )
                val product = catalogDao.getProduct(size.productId)
                audit.record(
                    AuditEntityType.PRODUCT, size.productId, AuditAction.UPDATE,
                    "Updated recipe for \"${product?.name} · ${size.name}\"", details = description
                )
            }
            is RecipeTarget.AddOn -> {
                val addOn = catalogDao.getAddOn(target.addOnId) ?: throw UserFacingException("Add-on not found")
                catalogDao.deleteAddOnRecipe(addOn.id)
                catalogDao.insertAddOnRecipeItems(
                    lines.map { AddOnRecipeItemEntity(addOnId = addOn.id, ingredientId = it.ingredientId, quantityMilli = it.quantityMilli) }
                )
                audit.record(
                    AuditEntityType.ADD_ON, addOn.id, AuditAction.UPDATE,
                    "Updated recipe for \"${addOn.name}\"", details = description
                )
            }
        }
    }

    // ---------- Helpers ----------

    private suspend fun activeIngredient(id: Long): IngredientEntity {
        val ingredient = dao.getIngredient(id) ?: throw UserFacingException("Ingredient not found")
        if (ingredient.isArchived) throw UserFacingException("\"${ingredient.name}\" is archived. Restore it first.")
        return ingredient
    }

    private suspend fun checkNameFree(name: String, excludeId: Long) {
        if (dao.countIngredientsNamed(name, excludeId) > 0) {
            throw UserFacingException("An ingredient named \"$name\" already exists")
        }
    }

    private fun cleanName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) throw UserFacingException("Enter an ingredient name")
        if (clean.length > MAX_NAME_LENGTH) throw UserFacingException("Keep the name under $MAX_NAME_LENGTH characters")
        return clean
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
    }
}

