package com.project2.coffeeshoppos.domain

import java.math.BigDecimal
import java.math.RoundingMode

// ---------- What the POS sells ----------

/** One sellable product as the POS shows it: only available sizes and add-ons are included. */
data class PosProduct(
    val id: Long,
    val name: String,
    val categoryId: Long?,
    /** In display order; the first is the default. Never empty. */
    val sizes: List<PosSize>,
    val addOns: List<PosAddOn>,
)

data class PosSize(val id: Long, val name: String, val priceCentavos: Long)
data class PosAddOn(val id: Long, val name: String, val priceCentavos: Long)

/** What the POS sells right now. Products with no sizes or marked sold out are left out. */
data class PosMenu(val categories: List<Pair<Long, String>>, val products: List<PosProduct>)

// ---------- The cart ----------

/**
 * One cart row as the cashier built it. Only ids and counts are kept: names and prices are
 * looked up from the live menu every time, so the cart never charges an outdated price.
 */
data class CartLine(
    val key: Long,
    val productId: Long,
    val variationId: Long,
    /** Add-on id -> how many go on ONE item, e.g. 2 extra shots. */
    val addOns: Map<Long, Int>,
    val quantity: Int,
    val note: String = "",
)

data class PricedAddOn(val addOnId: Long, val name: String, val priceCentavos: Long, val quantityPerItem: Int)

/** A cart row with today's names and prices, or a reason it can no longer be sold. */
data class PricedCartLine(
    val line: CartLine,
    val productName: String,
    val sizeName: String,
    /** True when the product has a single size, so the size name is not worth showing. */
    val isOnlySize: Boolean,
    val unitPriceCentavos: Long,
    val addOns: List<PricedAddOn>,
    val unavailableReason: String? = null,
) {
    val addOnsUnitPriceCentavos: Long get() = addOns.sumOf { it.priceCentavos * it.quantityPerItem }
    val lineTotalCentavos: Long get() = (unitPriceCentavos + addOnsUnitPriceCentavos) * line.quantity
    val title: String get() = if (isOnlySize) productName else "$productName · $sizeName"
}

/** Looks up a cart line in the live menu. */
fun priceCartLine(line: CartLine, menu: PosMenu): PricedCartLine {
    val product = menu.products.firstOrNull { it.id == line.productId }
    val size = product?.sizes?.firstOrNull { it.id == line.variationId }
    if (product == null || size == null) {
        return PricedCartLine(line, "Item", "", true, 0, emptyList(), unavailableReason = "No longer available")
    }
    val addOns = line.addOns.filterValues { it > 0 }.map { (id, count) ->
        val addOn = product.addOns.firstOrNull { it.id == id }
        PricedAddOn(id, addOn?.name ?: "Add-on", addOn?.priceCentavos ?: 0, count)
    }
    val missingAddOn = line.addOns.filterValues { it > 0 }.keys.any { id -> product.addOns.none { it.id == id } }
    return PricedCartLine(
        line = line,
        productName = product.name,
        sizeName = size.name,
        isOnlySize = product.sizes.size == 1,
        unitPriceCentavos = size.priceCentavos,
        addOns = addOns,
        unavailableReason = if (missingAddOn) "An add-on is no longer available" else null
    )
}

sealed interface Discount {
    data object None : Discount
    data class Percent(val percent: Int) : Discount
    data class Amount(val centavos: Long) : Discount
}

/** Money math for an order. Everything is whole centavos. */
object CartMath {
    fun subtotal(lines: List<PricedCartLine>): Long = lines.sumOf { it.lineTotalCentavos }

    /** The discount in centavos, rounded to the nearest centavo and never more than the subtotal. */
    fun discountAmount(subtotalCentavos: Long, discount: Discount): Long = when (discount) {
        Discount.None -> 0
        is Discount.Percent -> BigDecimal.valueOf(subtotalCentavos)
            .multiply(BigDecimal.valueOf(discount.percent.toLong()))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
            .toLong()
        is Discount.Amount -> discount.centavos
    }.coerceIn(0, subtotalCentavos)

    fun total(subtotalCentavos: Long, discount: Discount): Long = subtotalCentavos - discountAmount(subtotalCentavos, discount)

    /** Quick cash buttons: the exact amount, then the next round ₱50, ₱100, ₱500 and ₱1,000. */
    fun suggestedCash(totalCentavos: Long): List<Long> {
        if (totalCentavos <= 0) return emptyList()
        return (listOf(totalCentavos) + listOf(5_000L, 10_000L, 50_000L, 100_000L).map { step ->
            (totalCentavos + step - 1) / step * step
        }).distinct().take(4)
    }
}

// ---------- Saving an order ----------

data class PaymentInput(
    val method: PaymentMethod,
    /** Cash handed over; only for cash payments. */
    val cashReceivedCentavos: Long? = null,
    /** e.g. a GCash reference number. */
    val reference: String = "",
)

data class OrderDraft(
    val lines: List<CartLine>,
    val discount: Discount,
    val discountReason: String,
    val note: String,
    val payment: PaymentInput,
    /** For grab-and-go items: skip the queue and take the stock out right away. */
    val completeNow: Boolean,
)

data class PlacedOrder(val orderId: Long, val orderNumber: String, val totalCentavos: Long, val changeCentavos: Long?)

/** Orders that are paid for but not yet handed over. */
val OrderStatus.isActive: Boolean get() = this == OrderStatus.PENDING || this == OrderStatus.PROCESSING
