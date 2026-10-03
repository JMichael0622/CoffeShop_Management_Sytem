package com.project2.coffeeshoppos.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** What a unit measures. Only units with the same dimension can be converted. */
enum class UnitDimension { MASS, VOLUME, COUNT }

/**
 * Units the owner can enter. Each ingredient is stored in one base unit (g, ml or pc),
 * and anything entered in kg or L is converted to that base unit before saving.
 */
enum class MeasureUnit(
    val symbol: String,
    val dimension: UnitDimension,
    val baseUnitsPerUnit: Long,
) {
    GRAM("g", UnitDimension.MASS, 1),
    KILOGRAM("kg", UnitDimension.MASS, 1_000),
    MILLILITER("ml", UnitDimension.VOLUME, 1),
    LITER("L", UnitDimension.VOLUME, 1_000),
    PIECE("pc", UnitDimension.COUNT, 1);

    val isBaseUnit: Boolean get() = baseUnitsPerUnit == 1L

    /** The base unit this unit converts into, e.g. KILOGRAM -> GRAM. */
    val baseUnit: MeasureUnit
        get() = entries.first { it.dimension == dimension && it.isBaseUnit }

    /** Units the owner may pick when entering a quantity of an ingredient stored in this unit. */
    val compatibleUnits: List<MeasureUnit>
        get() = entries.filter { it.dimension == dimension }

    /** The larger unit used to show big amounts and prices, e.g. 2.5 kg and ₱450/kg instead of grams. */
    val displayUnit: MeasureUnit
        get() = entries.filter { it.dimension == dimension }.maxBy { it.baseUnitsPerUnit }

    /** Name shown when the owner picks an ingredient's unit, e.g. "Grams (g)". */
    val label: String
        get() = when (this) {
            GRAM -> "Grams (g)"
            KILOGRAM -> "Kilograms (kg)"
            MILLILITER -> "Milliliters (ml)"
            LITER -> "Liters (L)"
            PIECE -> "Pieces (pc)"
        }

    /**
     * Converts an entered amount (e.g. 1.5 kg) to thousandths of [target] (e.g. 1,500,000 for grams).
     * Throws [IncompatibleUnitsException] for conversions like grams to milliliters.
     */
    fun toMilli(amount: BigDecimal, target: MeasureUnit): Long {
        if (dimension != target.dimension) throw IncompatibleUnitsException(this, target)
        val inTarget = amount
            .multiply(BigDecimal.valueOf(baseUnitsPerUnit))
            .divide(BigDecimal.valueOf(target.baseUnitsPerUnit))
        return inTarget.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }
}

class IncompatibleUnitsException(from: MeasureUnit, to: MeasureUnit) :
    IllegalArgumentException("Cannot convert ${from.symbol} to ${to.symbol}")

/** Quantities are stored as whole thousandths of the base unit: 18 g is stored as 18,000. */
const val MILLI_PER_UNIT = 1_000L

/** Converts a stored milli-quantity back to a readable number, e.g. 18,500 -> 18.5. */
fun milliToDecimal(milli: Long): BigDecimal = BigDecimal.valueOf(milli, 3).stripTrailingZeros()
