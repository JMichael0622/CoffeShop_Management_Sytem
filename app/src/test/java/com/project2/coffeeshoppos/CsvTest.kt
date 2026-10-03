package com.project2.coffeeshoppos

import com.project2.coffeeshoppos.data.export.CsvBuilder
import com.project2.coffeeshoppos.data.export.ExportFormats
import com.project2.coffeeshoppos.data.export.decimal
import com.project2.coffeeshoppos.data.export.money
import com.project2.coffeeshoppos.data.export.number
import com.project2.coffeeshoppos.data.export.text
import com.project2.coffeeshoppos.data.local.entity.ExpenseEntity
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.PaymentStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    @Test
    fun quotesOnlyWhenNeeded() {
        val csv = CsvBuilder(listOf("A", "B")).apply {
            row(text("plain"), text("has, comma"))
            row(text("say \"hi\""), text("two\nlines"))
            row(text(" padded "), text(null))
        }
        val lines = csv.build().removePrefix("﻿").split("\r\n")
        assertEquals("A,B", lines[0])
        assertEquals("plain,\"has, comma\"", lines[1])
        assertEquals("\"say \"\"hi\"\"\",\"two\nlines\"", lines[2])
        assertEquals("\" padded \",", lines[3])
        assertEquals(3, csv.rowCount)
    }

    @Test
    fun neverLetsTextRunAsASpreadsheetFormula() {
        val csv = CsvBuilder(listOf("Note")).apply {
            row(text("=HYPERLINK(\"http://x\")"))
            row(text("+63 917 000 0000"))
            row(text("@home"))
        }
        val lines = csv.build().removePrefix("﻿").split("\r\n")
        assertEquals("'=HYPERLINK(\"\"http://x\"\")".let { "\"$it\"" }, lines[1])
        assertEquals("'+63 917 000 0000", lines[2])
        assertEquals("'@home", lines[3])
    }

    @Test
    fun numbersAndMoneyStayNumeric() {
        val csv = CsvBuilder(listOf("Qty", "Amount", "Refund", "Kg")).apply {
            row(number(3), money(12_050), money(-500), decimal(BigDecimal("2.500")))
        }
        assertTrue(csv.build().startsWith("﻿"))
        assertEquals("3,120.50,-5.00,2.5", csv.build().removePrefix("﻿").split("\r\n")[1])
    }

    @Test
    fun expensesExportUsesReadableLabels() {
        val expense = ExpenseEntity(
            description = "Meralco, September", amountCentavos = 350_000, category = ExpenseCategory.ELECTRICITY,
            incurredDate = LocalDate.of(2026, 10, 1), paidDate = LocalDate.of(2026, 10, 2), paymentStatus = PaymentStatus.PAID,
            paymentMethod = PaymentMethod.GCASH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH
        )
        val line = ExportFormats(ZoneId.of("Asia/Manila")).expenses(listOf(expense)).build().split("\r\n")[1]
        assertEquals("2026-10-01,\"Meralco, September\",Electricity,3500.00,Paid,2026-10-02,GCash,,", line)
    }
}
