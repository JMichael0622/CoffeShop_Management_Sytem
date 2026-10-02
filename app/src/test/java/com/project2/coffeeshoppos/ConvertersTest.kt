package com.project2.coffeeshoppos

import com.project2.coffeeshoppos.data.local.Converters
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun bigDecimalRoundTripKeepsExactValue() {
        val value = BigDecimal("0.123456")
        val stored = converters.bigDecimalToString(value)
        assertEquals("0.123456", stored)
        assertEquals(value, converters.stringToBigDecimal(stored))
    }

    @Test
    fun datesAndInstantsRoundTrip() {
        val date = LocalDate.of(2026, 10, 2)
        assertEquals(date, converters.longToLocalDate(converters.localDateToLong(date)))

        val instant = Instant.ofEpochMilli(1_790_000_000_123)
        assertEquals(instant, converters.longToInstant(converters.instantToLong(instant)))
    }

    @Test
    fun nullsStayNull() {
        assertNull(converters.bigDecimalToString(null))
        assertNull(converters.longToInstant(null))
        assertNull(converters.longToLocalDate(null))
    }
}
