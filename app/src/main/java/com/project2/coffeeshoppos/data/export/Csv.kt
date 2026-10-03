package com.project2.coffeeshoppos.data.export

import java.math.BigDecimal

/**
 * Builds a CSV file that opens cleanly in Excel and Google Sheets.
 *
 * Text cells are quoted when needed, and text that starts like a formula (=, +, -, @) gets a
 * leading apostrophe so a spreadsheet never runs it. Money is written as plain pesos
 * (e.g. 120.50) so it can be summed.
 */
class CsvBuilder(header: List<String>) {
    private val out = StringBuilder()
    private val columns = header.size
    var rowCount = 0
        private set

    init {
        // A byte-order mark tells Excel the file is UTF-8, so ₱ and accented names show correctly.
        out.append('﻿')
        appendRow(header.map { Cell.Text(it) })
    }

    fun row(vararg cells: Cell) {
        require(cells.size == columns) { "Expected $columns cells, got ${cells.size}" }
        appendRow(cells.toList())
        rowCount++
    }

    private fun appendRow(cells: List<Cell>) {
        cells.joinTo(out, separator = ",") { it.encode() }
        out.append("\r\n")
    }

    fun build(): String = out.toString()
}

sealed interface Cell {
    fun encode(): String

    data class Text(val value: String) : Cell {
        override fun encode(): String {
            val guarded = if (value.isNotEmpty() && value.first() in FORMULA_STARTS) "'$value" else value
            val needsQuotes = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
                guarded != guarded.trim()
            return if (needsQuotes) "\"" + guarded.replace("\"", "\"\"") + "\"" else guarded
        }
    }

    /** A whole number such as a quantity. */
    data class Number(val value: Long) : Cell {
        override fun encode() = value.toString()
    }

    /** Centavos written as pesos with two decimals: 12050 -> 120.50, -500 -> -5.00. */
    data class Money(val centavos: Long) : Cell {
        override fun encode(): String = BigDecimal.valueOf(centavos, 2).toPlainString()
    }

    /** A decimal such as 2.5 (kg). */
    data class Decimal(val value: BigDecimal) : Cell {
        override fun encode(): String = value.stripTrailingZeros().toPlainString()
    }

    companion object {
        private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
        val Empty = Text("")
    }
}

fun text(value: String?) = if (value == null) Cell.Empty else Cell.Text(value)
fun number(value: Long) = Cell.Number(value)
fun number(value: Int) = Cell.Number(value.toLong())
fun money(centavos: Long?) = if (centavos == null) Cell.Empty else Cell.Money(centavos)
fun decimal(value: BigDecimal) = Cell.Decimal(value)
