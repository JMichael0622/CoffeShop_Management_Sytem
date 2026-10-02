package com.project2.coffeeshoppos.data.repository

import androidx.room.withTransaction
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.local.entity.AddOnEntity
import com.project2.coffeeshoppos.data.local.entity.CategoryEntity
import com.project2.coffeeshoppos.data.local.entity.ProductAddOnCrossRef
import com.project2.coffeeshoppos.data.local.entity.ProductEntity
import com.project2.coffeeshoppos.data.local.entity.ProductVariationEntity
import com.project2.coffeeshoppos.domain.AddOnDraft
import com.project2.coffeeshoppos.domain.AuditAction
import com.project2.coffeeshoppos.domain.ProductDraft
import com.project2.coffeeshoppos.domain.ProductSummary
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.util.formatPeso
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A product as loaded into the editor: only its active (non-archived) sizes are included. */
data class ProductEditData(
    val product: ProductEntity,
    val sizes: List<ProductVariationEntity>,
    val addOnIds: Set<Long>,
)

/**
 * Menu setup: categories, products, sizes and add-ons.
 *
 * Nothing that an order could refer to is ever deleted. Removed sizes, products, add-ons and
 * categories are archived instead, and orders keep their own copy of names and prices, so
 * editing the menu never rewrites history.
 */
class CatalogRepository(
    private val db: CoffeeShopDatabase,
    private val audit: AuditRepository,
    private val clock: Clock,
) {
    private val dao = db.catalogDao()

    // ---------- Reading ----------

    fun observeCategories(): Flow<List<CategoryEntity>> = dao.observeActiveCategories()
    fun observeAllCategories(): Flow<List<CategoryEntity>> = dao.observeAllCategories()
    fun observeProducts(): Flow<List<ProductEntity>> = dao.observeActiveProducts()
    fun observeProductCount(): Flow<Int> = dao.observeActiveProductCount()
    fun observeActiveAddOns(): Flow<List<AddOnEntity>> = dao.observeActiveAddOns()
    fun observeAllAddOns(): Flow<List<AddOnEntity>> = dao.observeAllAddOns()

    /** Every product (archived too) with its category name and price range. */
    fun observeProductSummaries(): Flow<List<ProductSummary>> = combine(
        dao.observeAllProducts(),
        dao.observeAllCategories(),
        dao.observeAllActiveVariations(),
    ) { products, categories, sizes ->
        val categoryNames = categories.associate { it.id to it.name }
        val pricesByProduct = sizes.groupBy({ it.productId }, { it.priceCentavos })
        products.map { product ->
            val prices = pricesByProduct[product.id].orEmpty()
            ProductSummary(
                id = product.id,
                name = product.name,
                categoryId = product.categoryId,
                categoryName = product.categoryId?.let(categoryNames::get),
                isAvailable = product.isAvailable,
                isArchived = product.isArchived,
                minPriceCentavos = prices.minOrNull(),
                maxPriceCentavos = prices.maxOrNull(),
                sizeCount = prices.size
            )
        }
    }

    suspend fun getProductForEdit(id: Long): ProductEditData? {
        val product = dao.getProduct(id) ?: return null
        return ProductEditData(
            product = product,
            sizes = dao.getVariations(id).filterNot { it.isArchived },
            addOnIds = dao.getProductAddOnIds(id).toSet()
        )
    }

    // ---------- Categories ----------

    suspend fun addCategory(name: String): Long = db.withTransaction {
        val clean = cleanName(name, "category")
        if (dao.countCategoriesNamed(clean, excludeId = 0) > 0) {
            throw UserFacingException("A category named \"$clean\" already exists")
        }
        val id = dao.insertCategory(
            CategoryEntity(name = clean, sortOrder = dao.nextCategorySortOrder(), createdAt = clock.instant())
        )
        audit.record(AuditEntityType.CATEGORY, id, AuditAction.CREATE, "Created category \"$clean\"")
        id
    }

    suspend fun renameCategory(id: Long, newName: String) = db.withTransaction {
        val category = dao.getCategory(id) ?: throw UserFacingException("Category not found")
        val clean = cleanName(newName, "category")
        if (clean == category.name) return@withTransaction
        if (dao.countCategoriesNamed(clean, excludeId = id) > 0) {
            throw UserFacingException("A category named \"$clean\" already exists")
        }
        dao.updateCategory(category.copy(name = clean))
        audit.record(
            AuditEntityType.CATEGORY, id, AuditAction.UPDATE,
            "Renamed category \"${category.name}\" to \"$clean\""
        )
    }

    suspend fun setCategoryArchived(id: Long, archived: Boolean) = db.withTransaction {
        val category = dao.getCategory(id) ?: throw UserFacingException("Category not found")
        if (archived) {
            val activeProducts = dao.countActiveProductsInCategory(id)
            if (activeProducts > 0) {
                throw UserFacingException(
                    "\"${category.name}\" still has $activeProducts active " +
                        (if (activeProducts == 1) "product" else "products") +
                        ". Move or archive them first."
                )
            }
        }
        dao.updateCategory(category.copy(isArchived = archived))
        audit.record(
            AuditEntityType.CATEGORY, id,
            if (archived) AuditAction.ARCHIVE else AuditAction.RESTORE,
            "${if (archived) "Archived" else "Restored"} category \"${category.name}\""
        )
    }

    /** Moves an active category one place up or down in the POS order. */
    suspend fun moveCategory(id: Long, up: Boolean) = db.withTransaction {
        val categories = dao.getActiveCategories().toMutableList()
        val index = categories.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in categories.indices) return@withTransaction
        categories.add(target, categories.removeAt(index))
        dao.updateCategories(categories.mapIndexed { position, category -> category.copy(sortOrder = position) })
    }

    // ---------- Products ----------

    /** Saves a new or edited product, its sizes and its add-ons in one transaction. Returns its id. */
    suspend fun saveProduct(draft: ProductDraft): Long = db.withTransaction {
        val now = clock.instant()
        val name = cleanName(draft.name, "product")
        if (draft.sizes.isEmpty()) throw UserFacingException("Add at least one size with a price")
        if (draft.sizes.any { it.priceCentavos < 0 }) throw UserFacingException("Prices cannot be negative")

        val existing = if (draft.id == 0L) null else {
            dao.getProduct(draft.id) ?: throw UserFacingException("Product not found")
        }
        val productId = if (existing == null) {
            dao.insertProduct(
                ProductEntity(
                    categoryId = draft.categoryId,
                    name = name,
                    description = draft.description.trim(),
                    isAvailable = draft.isAvailable,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            dao.updateProduct(
                existing.copy(
                    categoryId = draft.categoryId,
                    name = name,
                    description = draft.description.trim(),
                    isAvailable = draft.isAvailable,
                    updatedAt = now
                )
            )
            existing.id
        }

        val changes = mutableListOf<String>()
        if (existing != null && existing.name != name) changes += "Name: \"${existing.name}\" → \"$name\""
        if (existing != null && existing.categoryId != draft.categoryId) changes += "Category changed"
        if (existing != null && existing.isAvailable != draft.isAvailable) {
            changes += if (draft.isAvailable) "Marked available" else "Marked unavailable"
        }

        // Sizes: update the kept ones, add new ones, archive removed ones (never delete).
        val currentSizes = dao.getVariations(productId)
        val currentById = currentSizes.associateBy { it.id }
        val keptIds = draft.sizes.map { it.id }.filter { it != 0L }.toSet()

        currentSizes.filter { !it.isArchived && it.id !in keptIds }.forEach { removed ->
            dao.updateVariation(removed.copy(isArchived = true, isDefault = false))
            changes += "Removed size \"${removed.name}\""
        }
        draft.sizes.forEachIndexed { position, size ->
            val current = if (size.id == 0L) null else {
                currentById[size.id]?.takeIf { it.productId == productId }
                    ?: throw UserFacingException("Size not found")
            }
            if (current == null) {
                dao.insertVariation(
                    ProductVariationEntity(
                        productId = productId,
                        name = size.name,
                        priceCentavos = size.priceCentavos,
                        isDefault = position == 0,
                        sortOrder = position
                    )
                )
                if (existing != null) changes += "Added size \"${size.name}\" at ${formatPeso(size.priceCentavos)}"
            } else {
                dao.updateVariation(
                    current.copy(
                        name = size.name,
                        priceCentavos = size.priceCentavos,
                        isDefault = position == 0,
                        isArchived = false,
                        sortOrder = position
                    )
                )
                if (current.name != size.name) changes += "Size \"${current.name}\" renamed to \"${size.name}\""
                if (current.priceCentavos != size.priceCentavos) {
                    changes += "\"${size.name}\" price: ${formatPeso(current.priceCentavos)} → " +
                        formatPeso(size.priceCentavos)
                }
            }
        }

        // Add-ons offered with this product.
        val previousAddOns = dao.getProductAddOnIds(productId).toSet()
        if (previousAddOns != draft.addOnIds) {
            dao.deleteProductAddOns(productId)
            dao.insertProductAddOns(draft.addOnIds.map { ProductAddOnCrossRef(productId, it) })
            if (existing != null) changes += "Add-ons changed"
        }

        if (existing == null) {
            audit.record(
                AuditEntityType.PRODUCT, productId, AuditAction.CREATE, "Created product \"$name\"",
                details = draft.sizes.joinToString("\n") { "${it.name}: ${formatPeso(it.priceCentavos)}" }
            )
        } else if (changes.isNotEmpty()) {
            audit.record(
                AuditEntityType.PRODUCT, productId, AuditAction.UPDATE, "Updated product \"$name\"",
                details = changes.joinToString("\n")
            )
        }
        productId
    }

    /** Quick "sold out today" switch from the menu list. */
    suspend fun setProductAvailable(id: Long, available: Boolean) {
        dao.setProductAvailable(id, available, clock.instant())
    }

    suspend fun setProductArchived(id: Long, archived: Boolean) = db.withTransaction {
        val product = dao.getProduct(id) ?: throw UserFacingException("Product not found")
        if (!archived && product.categoryId != null && dao.getCategory(product.categoryId)?.isArchived == true) {
            throw UserFacingException("Restore its category first, or move the product to another category")
        }
        dao.updateProduct(product.copy(isArchived = archived, updatedAt = clock.instant()))
        audit.record(
            AuditEntityType.PRODUCT, id,
            if (archived) AuditAction.ARCHIVE else AuditAction.RESTORE,
            "${if (archived) "Archived" else "Restored"} product \"${product.name}\""
        )
    }

    // ---------- Add-ons ----------

    suspend fun saveAddOn(draft: AddOnDraft): Long = db.withTransaction {
        val name = cleanName(draft.name, "add-on")
        if (draft.priceCentavos < 0) throw UserFacingException("Price cannot be negative")
        if (dao.countAddOnsNamed(name, excludeId = draft.id) > 0) {
            throw UserFacingException("An add-on named \"$name\" already exists")
        }
        if (draft.id == 0L) {
            val id = dao.insertAddOn(
                AddOnEntity(
                    name = name,
                    priceCentavos = draft.priceCentavos,
                    isAvailable = draft.isAvailable,
                    createdAt = clock.instant()
                )
            )
            audit.record(
                AuditEntityType.ADD_ON, id, AuditAction.CREATE,
                "Created add-on \"$name\" at ${formatPeso(draft.priceCentavos)}"
            )
            id
        } else {
            val current = dao.getAddOn(draft.id) ?: throw UserFacingException("Add-on not found")
            dao.updateAddOn(current.copy(name = name, priceCentavos = draft.priceCentavos, isAvailable = draft.isAvailable))
            val changes = buildList {
                if (current.name != name) add("Name: \"${current.name}\" → \"$name\"")
                if (current.priceCentavos != draft.priceCentavos) {
                    add("Price: ${formatPeso(current.priceCentavos)} → ${formatPeso(draft.priceCentavos)}")
                }
                if (current.isAvailable != draft.isAvailable) {
                    add(if (draft.isAvailable) "Marked available" else "Marked unavailable")
                }
            }
            if (changes.isNotEmpty()) {
                audit.record(
                    AuditEntityType.ADD_ON, current.id, AuditAction.UPDATE, "Updated add-on \"$name\"",
                    details = changes.joinToString("\n")
                )
            }
            current.id
        }
    }

    suspend fun setAddOnArchived(id: Long, archived: Boolean) = db.withTransaction {
        val addOn = dao.getAddOn(id) ?: throw UserFacingException("Add-on not found")
        dao.updateAddOn(addOn.copy(isArchived = archived))
        audit.record(
            AuditEntityType.ADD_ON, id,
            if (archived) AuditAction.ARCHIVE else AuditAction.RESTORE,
            "${if (archived) "Archived" else "Restored"} add-on \"${addOn.name}\""
        )
    }

    private fun cleanName(name: String, what: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) throw UserFacingException("Enter a $what name")
        if (clean.length > MAX_NAME_LENGTH) {
            throw UserFacingException("Keep the $what name under $MAX_NAME_LENGTH characters")
        }
        return clean
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
    }
}
