package com.project2.coffeeshoppos.data.export

import com.project2.coffeeshoppos.data.local.dao.CustomerSummary
import com.project2.coffeeshoppos.data.local.dao.ExportLineRow
import com.project2.coffeeshoppos.data.local.dao.ExportMovementRow
import com.project2.coffeeshoppos.data.local.dao.ExportOrderRow
import com.project2.coffeeshoppos.data.local.dao.ExportPurchaseRow
import com.project2.coffeeshoppos.data.local.entity.ExpenseEntity
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.domain.LoyaltyMath
import com.project2.coffeeshoppos.domain.milliToDecimal
import com.project2.coffeeshoppos.domain.stockValueCentavos
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What can be saved from the Export screen. [needsRange] is false for snapshots of "now". */
enum class ExportType(val label: String, val description: String, val fileStem: String, val mimeType: String, val needsRange: Boolean = true) {
    PROFIT_SUMMARY_PDF("Profit summary", "PDF report to print or share", "profit-summary", "application/pdf"),
    ORDERS_CSV("Orders", "One row per order, with payment and refunds", "orders", CSV),
    ITEMS_CSV("Items sold", "One row per item on each order", "items-sold", CSV),
    EXPENSES_CSV("Expenses", "Operating expenses by date", "expenses", CSV),
    PURCHASES_CSV("Stock purchases", "What was bought, from whom, for how much", "stock-purchases", CSV),
    MOVEMENTS_CSV("Stock movements", "Every change to stock, with its cost", "stock-movements", CSV),
    STOCK_CSV("Current stock", "Every ingredient's stock and value right now", "current-stock", CSV, needsRange = false),
    CUSTOMERS_CSV("Customers", "Loyalty customers with stamps and visits", "customers", CSV, needsRange = false);

    val extension: String get() = if (mimeType == CSV) "csv" else "pdf"
}

private const val CSV = "text/csv"
private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** Turns database rows into CSV text. Times are written in the shop's time zone. */
class ExportFormats(private val zone: ZoneId) {
    private fun Instant?.local(): String = this?.atZone(zone)?.format(DATE_TIME) ?: ""

    fun orders(rows: List<ExportOrderRow>): CsvBuilder = CsvBuilder(
        listOf(
            "Order no.", "Taken", "Completed", "Status", "Subtotal", "Discount", "Discount reason", "Loyalty free drink", "Total",
            "Refunded", "Ingredient cost", "Payment method", "Payment reference", "Note"
        )
    ).apply {
        rows.forEach { row ->
            val o = row.order
            row(
                text(o.orderNumber), text(o.createdAt.local()), text(o.completedAt.local()), text(o.status.label),
                money(o.subtotalCentavos), money(o.discountCentavos), text(o.discountReason), money(o.loyaltyRewardCentavos), money(o.totalCentavos),
                money(row.refundedCentavos), money(o.cogsCentavos), text(row.paymentMethod?.label), text(row.paymentReference),
                text(o.note)
            )
        }
    }

    fun items(rows: List<ExportLineRow>): CsvBuilder = CsvBuilder(
        listOf(
            "Order no.", "Taken", "Order status", "Product", "Size", "Quantity", "Unit price", "Add-ons",
            "Add-ons per item", "Line total", "Ingredient cost", "Note"
        )
    ).apply {
        rows.forEach { row ->
            val l = row.line
            row(
                text(row.orderNumber), text(row.orderCreatedAt.local()), text(row.orderStatus.label), text(l.productName),
                text(l.variationName), number(l.quantity), money(l.unitPriceCentavos), text(row.addOns),
                money(l.addOnsUnitPriceCentavos), money(l.lineTotalCentavos), money(l.cogsCentavos), text(l.note)
            )
        }
    }

    fun expenses(rows: List<ExpenseEntity>): CsvBuilder = CsvBuilder(
        listOf("Date", "Description", "Category", "Amount", "Status", "Paid on", "Method", "Reference", "Notes")
    ).apply {
        rows.forEach { e ->
            row(
                text(e.incurredDate.toString()), text(e.description), text(e.category.label), money(e.amountCentavos),
                text(e.paymentStatus.label), text(e.paidDate?.toString()), text(e.paymentMethod?.label), text(e.reference),
                text(e.notes)
            )
        }
    }

    fun purchases(rows: List<ExportPurchaseRow>): CsvBuilder = CsvBuilder(
        listOf("Bought", "Ingredient", "Quantity", "Unit", "Total cost", "Supplier", "Reference", "Notes")
    ).apply {
        rows.forEach { row ->
            val p = row.purchase
            row(
                text(p.purchasedAt.local()), text(row.ingredientName), decimal(p.enteredQuantity), text(p.enteredUnit.symbol),
                money(p.totalCostCentavos), text(p.supplier), text(p.reference), text(p.notes)
            )
        }
    }

    fun movements(rows: List<ExportMovementRow>): CsvBuilder = CsvBuilder(
        listOf("When", "Ingredient", "Type", "Change", "Stock after", "Unit", "Value", "Reason")
    ).apply {
        rows.forEach { row ->
            val m = row.movement
            row(
                text(m.createdAt.local()), text(row.ingredientName), text(m.type.label),
                decimal(milliToDecimal(m.quantityChangeMilli)), decimal(milliToDecimal(m.quantityAfterMilli)),
                text(row.ingredientUnit.symbol), money(m.costCentavos), text(m.reason)
            )
        }
    }

    fun customers(rows: List<CustomerSummary>): CsvBuilder = CsvBuilder(
        listOf("Name", "Mobile number", "Stamps", "Visits", "Total spent", "Last visit", "Customer since", "Note")
    ).apply {
        rows.forEach { s ->
            val c = s.customer
            row(
                text(c.name), text(c.phone?.let(LoyaltyMath::formatPhone)), number(s.stamps), number(s.visits),
                money(s.totalSpentCentavos), text(s.lastVisitAt.local()), text(c.createdAt.local()), text(c.note)
            )
        }
    }

    fun stock(rows: List<IngredientEntity>): CsvBuilder = CsvBuilder(
        listOf("Ingredient", "Group", "On hand", "Unit", "Reorder level", "Average cost per unit", "Stock value", "Archived")
    ).apply {
        rows.forEach { i ->
            row(
                text(i.name), text(i.category), decimal(milliToDecimal(i.quantityOnHandMilli)), text(i.baseUnit.symbol),
                decimal(milliToDecimal(i.reorderLevelMilli)),
                // Centavos per unit -> pesos per unit, to 4 decimals (e.g. 0.9000 per gram).
                decimal(i.averageCostCentavosPerUnit.movePointLeft(2).setScale(4, RoundingMode.HALF_UP)),
                money(i.stockValueCentavos), text(if (i.isArchived) "Yes" else "No")
            )
        }
    }
}
