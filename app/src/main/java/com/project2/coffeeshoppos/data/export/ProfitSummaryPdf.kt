package com.project2.coffeeshoppos.data.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.project2.coffeeshoppos.data.repository.SalesReport
import com.project2.coffeeshoppos.domain.costPercent
import com.project2.coffeeshoppos.util.formatPeso
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
private val STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH)

/**
 * Writes a one-to-two page A4 profit summary: the profit statement, orders, top products,
 * payment methods, expenses by category and stock. Same numbers as the Reports tab.
 */
fun writeProfitSummaryPdf(report: SalesReport, generatedAt: LocalDateTime, out: OutputStream) {
    val writer = PdfWriter()
    val s = report.summary
    val range = report.range
    writer.title("Profit summary")
    writer.note(
        (if (range.isSingleDay) range.start.format(DAY) else "${range.start.format(DAY)} – ${range.endInclusive.format(DAY)}") +
            " · generated ${generatedAt.format(STAMP)}"
    )

    writer.heading("Profit")
    writer.row("Gross sales", formatPeso(s.grossSalesCentavos))
    writer.row("Discounts", "−" + formatPeso(s.discountsCentavos))
    writer.row("Refunds", "−" + formatPeso(s.refundsCentavos))
    writer.row("Net sales", formatPeso(s.netSalesCentavos), bold = true)
    writer.row("Cost of goods", "−" + formatPeso(s.cogsCentavos))
    val margin = if (s.netSalesCentavos > 0) costPercent(s.grossProfitCentavos, s.netSalesCentavos) else null
    writer.row("Gross profit" + (margin?.let { " ($it%)" } ?: ""), formatPeso(s.grossProfitCentavos), bold = true)
    writer.row("Stock losses", (if (s.stockLossCentavos >= 0) "−" else "+") + formatPeso(kotlin.math.abs(s.stockLossCentavos)))
    writer.row("Operating expenses", "−" + formatPeso(s.operatingExpensesCentavos))
    writer.row("Net profit", formatPeso(s.netProfitCentavos), bold = true)

    writer.heading("Orders")
    writer.row("Completed", s.orderCount.toString())
    writer.row("Average order (after discounts)", s.averageOrderCentavos?.let(::formatPeso) ?: "—")
    writer.row("Cancelled", report.cancelledCount.toString())
    writer.row("Refunded", report.refundedCount.toString())

    if (report.productSales.isNotEmpty()) {
        writer.heading("Top products")
        report.productSales.take(15).forEach { writer.row("${it.productName} × ${it.quantity}", formatPeso(it.salesCentavos)) }
    }
    if (report.salesByMethod.isNotEmpty()) {
        writer.heading("Paid by")
        report.salesByMethod.forEach { writer.row(it.method.label, formatPeso(it.amountCentavos)) }
    }
    if (report.expensesByCategory.isNotEmpty()) {
        writer.heading("Operating expenses")
        report.expensesByCategory.forEach { (category, amount) -> writer.row(category.label, formatPeso(amount)) }
    }
    writer.heading("Stock")
    writer.row("Stock purchased (cash spent)", formatPeso(report.purchasesCentavos))
    writer.row("Wastage", formatPeso(report.wastageCentavos))

    writer.gap()
    writer.note(
        "Sales count when an order is completed; refunds on the day they are given; expenses on their date. " +
            "Stock purchases are cash spent, not a cost in profit: ingredients count as cost of goods when sold."
    )
    writer.finish(out)
}

/** A tiny top-to-bottom text layout on A4 pages, starting a new page when one fills up. */
private class PdfWriter {
    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var y = 0f
    private var pageNumber = 0

    private val titlePaint = paint(20f, bold = true)
    private val headingPaint = paint(13f, bold = true)
    private val bodyPaint = paint(11f)
    private val boldPaint = paint(11f, bold = true)
    private val notePaint = paint(9f).apply { color = 0xFF6B5B4E.toInt() }

    private fun paint(size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        color = 0xFF2B1D14.toInt()
    }

    private fun ensureRoom(height: Float) {
        val current = page
        if (current != null && y + height <= PAGE_HEIGHT - MARGIN) return
        current?.let(document::finishPage)
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        y = MARGIN
    }

    private val canvas get() = requireNotNull(page).canvas

    fun title(text: String) {
        ensureRoom(30f)
        y += 20f
        canvas.drawText(text, MARGIN, y, titlePaint)
        y += 8f
    }

    fun heading(text: String) {
        ensureRoom(40f)
        y += 26f
        canvas.drawText(text, MARGIN, y, headingPaint)
        y += 6f
    }

    fun row(label: String, value: String, bold: Boolean = false) {
        ensureRoom(18f)
        y += 16f
        val paint = if (bold) boldPaint else bodyPaint
        canvas.drawText(label, MARGIN, y, paint)
        canvas.drawText(value, PAGE_WIDTH - MARGIN - paint.measureText(value), y, paint)
    }

    fun gap() {
        y += 12f
    }

    /** Small grey text, wrapped to the page width. */
    fun note(text: String) {
        val maxWidth = PAGE_WIDTH - 2 * MARGIN
        val lines = mutableListOf<String>()
        var line = ""
        text.split(' ').forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (notePaint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                lines += line
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) lines += line
        lines.forEach {
            ensureRoom(14f)
            y += 13f
            canvas.drawText(it, MARGIN, y, notePaint)
        }
    }

    fun finish(out: OutputStream) {
        ensureRoom(0f)
        page?.let(document::finishPage)
        document.writeTo(out)
        document.close()
    }

    private companion object {
        // A4 in PostScript points.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
    }
}
