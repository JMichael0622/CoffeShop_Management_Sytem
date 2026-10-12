package com.project2.coffeeshoppos

import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.domain.InventoryMath
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.domain.RecipeLine
import com.project2.coffeeshoppos.domain.StockLevel
import com.project2.coffeeshoppos.domain.costPercent
import com.project2.coffeeshoppos.domain.formatQuantity
import com.project2.coffeeshoppos.domain.formatQuantityChange
import com.project2.coffeeshoppos.domain.formatUnitCost
import com.project2.coffeeshoppos.domain.milliToInput
import com.project2.coffeeshoppos.domain.parseQuantity
import com.project2.coffeeshoppos.domain.parseQuantityMilli
import com.project2.coffeeshoppos.domain.recipeCostCentavos
import com.project2.coffeeshoppos.domain.stockLevel
import com.project2.coffeeshoppos.domain.stockValueCentavos
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InventoryMathTest {
    @Test
    fun convertsPricePerDisplayUnitToCostPerBaseUnit() {
        // ₱1,000 per kg = 100 centavos per gram; ₱120 per L = 12 centavos per ml; ₱15 per piece.
        assertEquals(BigDecimal("100.000000"), InventoryMath.unitCostFromDisplayPrice(100_000, MeasureUnit.GRAM))
        assertEquals(BigDecimal("12.000000"), InventoryMath.unitCostFromDisplayPrice(12_000, MeasureUnit.MILLILITER))
        assertEquals(BigDecimal("1500.000000"), InventoryMath.unitCostFromDisplayPrice(1_500, MeasureUnit.PIECE))
        assertEquals(100_000L, InventoryMath.displayPriceCentavos(BigDecimal("100.000000"), MeasureUnit.GRAM))
    }

    private fun ingredient(onHand: Long, reorder: Long = 0, cost: String = "0", unit: MeasureUnit = MeasureUnit.GRAM) =
        IngredientEntity(
            id = 1, name = "Beans", baseUnit = unit, quantityOnHandMilli = onHand,
            averageCostCentavosPerUnit = BigDecimal(cost), reorderLevelMilli = reorder,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )

    @Test
    fun unitCostIsCentavosPerBaseUnit() {
        // ₱900 for 1 kg = 90 centavos per gram.
        assertEquals(BigDecimal("90.000000"), InventoryMath.unitCost(90_000, 1_000_000))
        // ₱100 for 3 pieces keeps 6 decimals.
        assertEquals(BigDecimal("3333.333333"), InventoryMath.unitCost(10_000, 3_000))
    }

    @Test
    fun costOfRoundsToWholeCentavos() {
        // 18 g at 90 centavos/g = ₱16.20
        assertEquals(1_620L, InventoryMath.costOf(18_000, BigDecimal("90")))
        // 0.5 g at 0.333333 centavos/g = 0.17 centavos -> 0
        assertEquals(0L, InventoryMath.costOf(500, BigDecimal("0.333333")))
        assertEquals(-1_620L, InventoryMath.costOf(-18_000, BigDecimal("90")))
    }

    @Test
    fun weightedAverageBlendsOldAndNewStock() {
        // 1 kg at ₱0.90/g plus 1 kg bought for ₱1,100 -> ₱1.00/g.
        val average = InventoryMath.weightedAverage(1_000_000, BigDecimal("90"), 1_000_000, 110_000)
        assertEquals(BigDecimal("100.000000"), average)
    }

    @Test
    fun weightedAverageUsesNewCostWhenStockIsEmptyOrShort() {
        assertEquals(BigDecimal("110.000000"), InventoryMath.weightedAverage(0, BigDecimal("90"), 1_000_000, 110_000))
        assertEquals(BigDecimal("110.000000"), InventoryMath.weightedAverage(-50_000, BigDecimal("90"), 1_000_000, 110_000))
    }

    @Test
    fun stockLevelsAndValue() {
        assertEquals(StockLevel.OUT, ingredient(onHand = 0).stockLevel)
        assertEquals(StockLevel.OUT, ingredient(onHand = -5_000).stockLevel)
        assertEquals(StockLevel.LOW, ingredient(onHand = 500_000, reorder = 500_000).stockLevel)
        assertEquals(StockLevel.OK, ingredient(onHand = 500_001, reorder = 500_000).stockLevel)
        assertEquals(StockLevel.OK, ingredient(onHand = 1_000).stockLevel) // no reorder level set
        assertEquals(45_000L, ingredient(onHand = 500_000, cost = "90").stockValueCentavos)
        assertEquals(0L, ingredient(onHand = -500_000, cost = "90").stockValueCentavos)
    }

    @Test
    fun formatsQuantitiesInReadableUnits() {
        assertEquals("18 g", formatQuantity(18_000, MeasureUnit.GRAM))
        assertEquals("999.5 g", formatQuantity(999_500, MeasureUnit.GRAM))
        assertEquals("2.5 kg", formatQuantity(2_500_000, MeasureUnit.GRAM))
        assertEquals("1,250 L", formatQuantity(1_250_000_000, MeasureUnit.MILLILITER))
        assertEquals("1,200 pc", formatQuantity(1_200_000, MeasureUnit.PIECE))
        assertEquals("-1.5 kg", formatQuantity(-1_500_000, MeasureUnit.GRAM))
        assertEquals("+250 ml", formatQuantityChange(250_000, MeasureUnit.MILLILITER))
        assertEquals("-18 g", formatQuantityChange(-18_000, MeasureUnit.GRAM))
    }

    @Test
    fun formatsUnitCostPerDisplayUnit() {
        assertEquals("₱900.00/kg", formatUnitCost(BigDecimal("90"), MeasureUnit.GRAM))
        assertEquals("₱95.00/L", formatUnitCost(BigDecimal("9.5"), MeasureUnit.MILLILITER))
        assertEquals("₱5.00/pc", formatUnitCost(BigDecimal("500"), MeasureUnit.PIECE))
    }

    @Test
    fun parsesTypedQuantities() {
        assertEquals(BigDecimal("2.5"), parseQuantity("2.5"))
        assertEquals(BigDecimal("0.5"), parseQuantity(".5"))
        assertEquals(BigDecimal("1000"), parseQuantity("1,000"))
        assertNull(parseQuantity(""))
        assertNull(parseQuantity("."))
        assertNull(parseQuantity("-1"))
        assertNull(parseQuantity("abc"))
        assertEquals(2_500_000L, parseQuantityMilli("2.5", MeasureUnit.KILOGRAM, MeasureUnit.GRAM))
        assertEquals("2.5", milliToInput(2_500_000, MeasureUnit.KILOGRAM))
        assertEquals("18", milliToInput(18_000, MeasureUnit.GRAM))
    }

    @Test
    fun recipeCostAndPercent() {
        val beans = ingredient(onHand = 0, cost = "90")
        val milk = ingredient(onHand = 0, cost = "9.5", unit = MeasureUnit.MILLILITER).copy(id = 2)
        val lines = listOf(RecipeLine(1, 18_000), RecipeLine(2, 200_000), RecipeLine(99, 1_000))
        // 18 g beans = ₱16.20, 200 ml milk = ₱19.00, unknown ingredient ignored.
        assertEquals(3_520L, recipeCostCentavos(lines, mapOf(1L to beans, 2L to milk)))
        assertEquals(29, costPercent(3_520, 12_000))
        assertNull(costPercent(3_520, 0))
    }
}
