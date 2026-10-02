package com.project2.coffeeshoppos

import com.project2.coffeeshoppos.util.centavosToInput
import com.project2.coffeeshoppos.util.formatPeso
import com.project2.coffeeshoppos.util.parsePesoToCentavos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun formatsWholeAndFractionalPesos() {
        assertEquals("₱0.00", formatPeso(0))
        assertEquals("₱0.05", formatPeso(5))
        assertEquals("₱125.50", formatPeso(12_550))
        assertEquals("₱1,234,567.89", formatPeso(123_456_789))
    }

    @Test
    fun formatsNegativeAmounts() {
        assertEquals("-₱45.00", formatPeso(-4_500))
    }

    @Test
    fun parsesTypedPrices() {
        assertEquals(12_000L, parsePesoToCentavos("120"))
        assertEquals(12_050L, parsePesoToCentavos("120.5"))
        assertEquals(12_055L, parsePesoToCentavos(" 120.55 "))
        assertEquals(125_000L, parsePesoToCentavos("₱1,250.00"))
        assertEquals(12_000L, parsePesoToCentavos("120."))
        assertEquals(0L, parsePesoToCentavos("0"))
    }

    @Test
    fun rejectsInvalidPrices() {
        assertNull(parsePesoToCentavos(""))
        assertNull(parsePesoToCentavos("abc"))
        assertNull(parsePesoToCentavos("-5"))
        assertNull(parsePesoToCentavos("1.234"))
        assertNull(parsePesoToCentavos("1.2.3"))
        assertNull(parsePesoToCentavos("1234567890"))
    }

    @Test
    fun turnsCentavosBackIntoInputText() {
        assertEquals("120", centavosToInput(12_000))
        assertEquals("120.50", centavosToInput(12_050))
        assertEquals("0.05", centavosToInput(5))
    }
}
