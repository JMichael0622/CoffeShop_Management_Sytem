package com.project2.coffeeshoppos.domain

import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.util.formatPeso
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

/**
 * Stock costing with the weighted average method. Costs are centavos per ONE base unit
 * (e.g. per gram) kept to 6 decimals; amounts actually charged are rounded to whole centavos.
 */
object InventoryMath {
    const val COST_SCALE = 6

    /** Centavos per one base unit, e.g. ₱900 for 1,000 g -> 90 centavos per gram. */
    fun unitCost(totalCostCentavos: Long, quantityMilli: Long): BigDecimal {
        require(quantityMilli > 0) { "Quantity must be positive" }
        return BigDecimal.valueOf(totalCostCentavos)
            .multiply(BigDecimal.valueOf(MILLI_PER_UNIT))
            .divide(BigDecimal.valueOf(quantityMilli), COST_SCALE, RoundingMode.HALF_UP)
    }

    /** Value in whole centavos of [quantityMilli] at [unitCost] centavos per base unit. */
    fun costOf(quantityMilli: Long, unitCost: BigDecimal): Long =
        unitCost.multiply(BigDecimal.valueOf(quantityMilli))
            .movePointLeft(3)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

    /**
     * The new average cost after adding [addedMilli] that cost [addedCostCentavos] in total.
     * If there was no stock (or a shortfall from selling more than was recorded), the old
     * average no longer means anything, so the new stock's own cost is used.
     */
    fun weightedAverage(
        onHandMilli: Long,
        averageCost: BigDecimal,
        addedMilli: Long,
        addedCostCentavos: Long,
    ): BigDecimal {
        require(addedMilli > 0) { "Added quantity must be positive" }
        if (onHandMilli <= 0) return unitCost(addedCostCentavos, addedMilli)
        val currentValue = averageCost.multiply(BigDecimal.valueOf(onHandMilli)).movePointLeft(3)
        val totalValue = currentValue.add(BigDecimal.valueOf(addedCostCentavos))
        return totalValue
            .multiply(BigDecimal.valueOf(MILLI_PER_UNIT))
            .divide(BigDecimal.valueOf(onHandMilli + addedMilli), COST_SCALE, RoundingMode.HALF_UP)
    }
}

// ---------- Stock levels ----------

enum class StockLevel { OK, LOW, OUT }

/** Out when nothing is left; low when at or below the reorder level (if one is set). */
val IngredientEntity.stockLevel: StockLevel
    get() = when {
        quantityOnHandMilli <= 0 -> StockLevel.OUT
        reorderLevelMilli > 0 && quantityOnHandMilli <= reorderLevelMilli -> StockLevel.LOW
        else -> StockLevel.OK
    }

/** What the stock on hand is worth at its average cost. A shortfall counts as zero. */
val IngredientEntity.stockValueCentavos: Long
    get() = if (quantityOnHandMilli <= 0) 0 else InventoryMath.costOf(quantityOnHandMilli, averageCostCentavosPerUnit)

// ---------- Display ----------

private val QUANTITY_FORMAT = DecimalFormat("#,##0.###", DecimalFormatSymbols(Locale.US)).apply {
    roundingMode = RoundingMode.HALF_UP
}

/**
 * A stored quantity for people to read. Large amounts switch to kg or L:
 * 18,000 milli-grams of stock reads "18 g", 2,500,000 reads "2.5 kg".
 */
fun formatQuantity(milli: Long, baseUnit: MeasureUnit): String {
    val large = baseUnit.displayUnit
    val milliPerLarge = large.baseUnitsPerUnit * MILLI_PER_UNIT
    val unit = if (large != baseUnit && kotlin.math.abs(milli) >= milliPerLarge) large else baseUnit
    val value = BigDecimal.valueOf(milli)
        .divide(BigDecimal.valueOf(unit.baseUnitsPerUnit * MILLI_PER_UNIT), 3, RoundingMode.HALF_UP)
    return "${QUANTITY_FORMAT.format(value)} ${unit.symbol}"
}

/** A signed change for the stock history, e.g. "+2.5 kg" or "-18 g". */
fun formatQuantityChange(milli: Long, baseUnit: MeasureUnit): String =
    (if (milli > 0) "+" else "") + formatQuantity(milli, baseUnit)

/** An average cost in a size people think in, e.g. "₱450.00/kg" rather than 45 centavos per gram. */
fun formatUnitCost(costPerBaseUnit: BigDecimal, baseUnit: MeasureUnit): String {
    val unit = baseUnit.displayUnit
    val centavos = costPerBaseUnit.multiply(BigDecimal.valueOf(unit.baseUnitsPerUnit))
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()
    return "${formatPeso(centavos)}/${unit.symbol}"
}

/** A stored quantity back as text for an input field in [unit]: 2,500,000 (grams) in kg -> "2.5". */
fun milliToInput(milli: Long, unit: MeasureUnit): String =
    BigDecimal.valueOf(milli)
        .divide(BigDecimal.valueOf(unit.baseUnitsPerUnit * MILLI_PER_UNIT))
        .stripTrailingZeros()
        .toPlainString()

// ---------- Input ----------

// Up to 9 whole digits and 6 decimals, e.g. "2.5", "0.018", "1,000".
private val QUANTITY_INPUT = Regex("""\d{0,9}(\.\d{0,6})?""")

/** Reads a quantity typed by the owner. Returns null for anything that is not a non-negative number. */
fun parseQuantity(input: String): BigDecimal? {
    val cleaned = input.trim().replace(",", "")
    if (!QUANTITY_INPUT.matches(cleaned) || cleaned.none(Char::isDigit)) return null
    return BigDecimal(if (cleaned.startsWith(".")) "0$cleaned" else cleaned)
}

/** Reads a quantity typed in [unit] and converts it to milli of [baseUnit], or null if invalid. */
fun parseQuantityMilli(input: String, unit: MeasureUnit, baseUnit: MeasureUnit): Long? =
    parseQuantity(input)?.let { unit.toMilli(it, baseUnit) }

// ---------- Drafts saved by the repository ----------

/** A new or edited ingredient. Opening stock is only used when creating one (id = 0). */
data class IngredientDraft(
    val id: Long,
    val name: String,
    val category: String,
    val baseUnit: MeasureUnit,
    val reorderLevelMilli: Long,
    val openingQuantityMilli: Long = 0,
    val openingTotalCostCentavos: Long = 0,
)

data class PurchaseDraft(
    val ingredientId: Long,
    /** What the owner typed, kept for display: e.g. 2.5 in [enteredUnit] KILOGRAM. */
    val enteredQuantity: BigDecimal,
    val enteredUnit: MeasureUnit,
    val totalCostCentavos: Long,
    val supplier: String,
    val reference: String,
    val notes: String,
    val purchasedOn: LocalDate,
)

/** One ingredient line of a recipe, in thousandths of the ingredient's base unit. */
data class RecipeLine(val ingredientId: Long, val quantityMilli: Long)

/** What a recipe belongs to: one size of a product, or an add-on. */
sealed interface RecipeTarget {
    data class Variation(val variationId: Long) : RecipeTarget
    data class AddOn(val addOnId: Long) : RecipeTarget
}

/** Cost of making one unit of a recipe at today's average ingredient costs. */
fun recipeCostCentavos(lines: List<RecipeLine>, ingredients: Map<Long, IngredientEntity>): Long =
    lines.sumOf { line ->
        val ingredient = ingredients[line.ingredientId] ?: return@sumOf 0L
        InventoryMath.costOf(line.quantityMilli, ingredient.averageCostCentavosPerUnit)
    }

/** Share of the selling price spent on ingredients, e.g. 28 for 28%. Null when there is no price. */
fun costPercent(costCentavos: Long, priceCentavos: Long): Int? =
    if (priceCentavos <= 0) null
    else BigDecimal.valueOf(costCentavos * 100).divide(BigDecimal.valueOf(priceCentavos), 0, RoundingMode.HALF_UP).toInt()
