package com.project2.coffeeshoppos

import com.project2.coffeeshoppos.domain.IncompatibleUnitsException
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.domain.milliToDecimal
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasureUnitTest {
    @Test
    fun convertsLargerUnitsToBaseMilli() {
        assertEquals(1_500_000L, MeasureUnit.KILOGRAM.toMilli(BigDecimal("1.5"), MeasureUnit.GRAM))
        assertEquals(2_000_000L, MeasureUnit.LITER.toMilli(BigDecimal("2"), MeasureUnit.MILLILITER))
        assertEquals(18_000L, MeasureUnit.GRAM.toMilli(BigDecimal("18"), MeasureUnit.GRAM))
        assertEquals(50_000L, MeasureUnit.PIECE.toMilli(BigDecimal("50"), MeasureUnit.PIECE))
    }

    @Test
    fun keepsFractionsOfBaseUnits() {
        assertEquals(500L, MeasureUnit.GRAM.toMilli(BigDecimal("0.5"), MeasureUnit.GRAM))
        assertEquals(1L, MeasureUnit.KILOGRAM.toMilli(BigDecimal("0.000001"), MeasureUnit.GRAM))
    }

    @Test(expected = IncompatibleUnitsException::class)
    fun rejectsMassToVolume() {
        MeasureUnit.KILOGRAM.toMilli(BigDecimal.ONE, MeasureUnit.MILLILITER)
    }

    @Test(expected = IncompatibleUnitsException::class)
    fun rejectsPiecesToGrams() {
        MeasureUnit.PIECE.toMilli(BigDecimal.ONE, MeasureUnit.GRAM)
    }

    @Test
    fun findsBaseAndCompatibleUnits() {
        assertEquals(MeasureUnit.GRAM, MeasureUnit.KILOGRAM.baseUnit)
        assertEquals(MeasureUnit.MILLILITER, MeasureUnit.LITER.baseUnit)
        assertEquals(listOf(MeasureUnit.GRAM, MeasureUnit.KILOGRAM), MeasureUnit.GRAM.compatibleUnits)
    }

    @Test
    fun formatsMilliBackToDecimal() {
        assertEquals("18.5", milliToDecimal(18_500).toPlainString())
        assertEquals("180", milliToDecimal(180_000).toPlainString())
    }
}
