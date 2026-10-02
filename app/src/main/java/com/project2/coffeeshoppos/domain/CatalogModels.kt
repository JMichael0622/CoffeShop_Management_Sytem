package com.project2.coffeeshoppos.domain

import com.project2.coffeeshoppos.util.parsePesoToCentavos

/** An error whose message is written for the owner and can be shown on screen as-is. */
class UserFacingException(message: String) : Exception(message)

/** One row in the menu list. Prices come from the product's active sizes. */
data class ProductSummary(
    val id: Long,
    val name: String,
    val categoryId: Long?,
    val categoryName: String?,
    val isAvailable: Boolean,
    val isArchived: Boolean,
    val minPriceCentavos: Long?,
    val maxPriceCentavos: Long?,
    val sizeCount: Int,
)

/** Everything the product editor saves in one go. id = 0 means a new product. */
data class ProductDraft(
    val id: Long,
    val name: String,
    val categoryId: Long?,
    val description: String,
    val isAvailable: Boolean,
    /** In display order; the first one is the default. id = 0 means a new size. */
    val sizes: List<SizeInput>,
    val addOnIds: Set<Long>,
)

data class SizeInput(val id: Long, val name: String, val priceCentavos: Long)

data class AddOnDraft(
    val id: Long,
    val name: String,
    val priceCentavos: Long,
    val isAvailable: Boolean,
)

/** One size row as typed in the editor, before it is checked. */
data class SizeFormInput(val key: Long, val id: Long, val name: String, val priceText: String)

data class ProductFormErrors(
    val name: String? = null,
    /** Error message per size row, keyed by [SizeFormInput.key]. */
    val sizes: Map<Long, String> = emptyMap(),
    val general: String? = null,
) {
    val hasErrors: Boolean get() = name != null || sizes.isNotEmpty() || general != null
}

const val DEFAULT_SIZE_NAME = "Regular"

/** Checks the product editor's inputs. Kept separate from the screen so it can be unit-tested. */
object ProductFormValidator {
    fun validate(name: String, sizes: List<SizeFormInput>): ProductFormErrors {
        val nameError = if (name.isBlank()) "Enter a product name" else null
        if (sizes.isEmpty()) {
            return ProductFormErrors(name = nameError, general = "Add at least one size with a price")
        }

        val sizeErrors = mutableMapOf<Long, String>()
        val seenNames = mutableSetOf<String>()
        sizes.forEach { size ->
            val normalized = size.name.trim().lowercase()
            val error = when {
                parsePesoToCentavos(size.priceText) == null -> "Enter a valid price, e.g. 120 or 120.50"
                sizes.size > 1 && normalized.isEmpty() -> "Give each size a name, e.g. 12 oz"
                sizes.size > 1 && !seenNames.add(normalized) -> "Each size needs a different name"
                else -> null
            }
            if (error != null) sizeErrors[size.key] = error
        }
        return ProductFormErrors(name = nameError, sizes = sizeErrors)
    }

    /** Converts already-validated form rows into sizes ready to save. */
    fun toSizeInputs(sizes: List<SizeFormInput>): List<SizeInput> = sizes.map { size ->
        SizeInput(
            id = size.id,
            name = size.name.trim().ifEmpty { DEFAULT_SIZE_NAME },
            priceCentavos = requireNotNull(parsePesoToCentavos(size.priceText))
        )
    }
}
