package com.project2.coffeeshoppos.util

import java.math.BigDecimal
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Money is stored everywhere as whole centavos in a Long (₱1.00 = 100).
 * Integer math is exact, unlike Float/Double, so totals never drift by a centavo.
 */
fun formatPeso(centavos: Long): String {
    val sign = if (centavos < 0) "-" else ""
    val absolute = centavos.absoluteValue
    return String.format(Locale.US, "%s₱%,d.%02d", sign, absolute / 100, absolute % 100)
}

// Up to 9 digits of pesos (₱999,999,999) and at most 2 decimal places.
private val PESO_INPUT = Regex("""\d{1,9}(\.\d{0,2})?""")

/**
 * Reads a price typed by the owner, e.g. "120", "120.5", "₱1,250.00", into centavos.
 * Returns null for anything that is not a valid non-negative amount with at most 2 decimals.
 */
fun parsePesoToCentavos(input: String): Long? {
    val cleaned = input.trim().removePrefix("₱").replace(",", "").trim()
    if (!PESO_INPUT.matches(cleaned)) return null
    return BigDecimal(cleaned).movePointRight(2).longValueExact()
}

/** Turns centavos back into text for an input field: 12000 -> "120", 12050 -> "120.50". */
fun centavosToInput(centavos: Long): String {
    val pesos = centavos / 100
    val cents = centavos % 100
    return if (cents == 0L) pesos.toString() else String.format(Locale.US, "%d.%02d", pesos, cents)
}
