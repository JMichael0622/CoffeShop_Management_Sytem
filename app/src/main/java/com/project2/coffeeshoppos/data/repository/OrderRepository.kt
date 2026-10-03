package com.project2.coffeeshoppos.data.repository

import androidx.room.withTransaction
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.local.dao.OrderListRow
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import com.project2.coffeeshoppos.data.local.entity.OrderLineAddOnEntity
import com.project2.coffeeshoppos.data.local.entity.OrderLineEntity
import com.project2.coffeeshoppos.data.local.entity.PaymentEntity
import com.project2.coffeeshoppos.data.local.entity.RefundEntity
import com.project2.coffeeshoppos.domain.AuditAction
import com.project2.coffeeshoppos.domain.CartMath
import com.project2.coffeeshoppos.domain.Discount
import com.project2.coffeeshoppos.domain.InventoryMath
import com.project2.coffeeshoppos.domain.OrderDraft
import com.project2.coffeeshoppos.domain.OrderStatus
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.PlacedOrder
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.domain.isActive
import com.project2.coffeeshoppos.util.formatPeso
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class OrderLineDetail(val line: OrderLineEntity, val addOns: List<OrderLineAddOnEntity>)

data class OrderDetail(
    val order: OrderEntity,
    val lines: List<OrderLineDetail>,
    val payments: List<PaymentEntity>,
    val refunds: List<RefundEntity>,
)

/** "261003-007" -> "#007": the short number the cashier calls out. */
fun shortOrderNumber(orderNumber: String): String = "#" + orderNumber.substringAfter('-')

/**
 * Taking orders, payments, cancellations and refunds.
 *
 * Orders copy every name and price when they are placed, and their ingredient cost when they are
 * completed, so later menu edits and stock purchases never change an old order. Stock is taken
 * out only when an order is completed (handed over), not when it is paid for.
 */
class OrderRepository(
    private val db: CoffeeShopDatabase,
    private val inventory: InventoryRepository,
    private val audit: AuditRepository,
    private val clock: Clock,
) {
    private val dao = db.orderDao()
    private val catalogDao = db.catalogDao()

    // ---------- Reading ----------

    fun observeRecentOrders(limit: Int = 20): Flow<List<OrderEntity>> = dao.observeRecentOrders(limit)
    fun observeOrderCount(): Flow<Int> = dao.observeOrderCount()
    fun observeActiveOrders(): Flow<List<OrderListRow>> = dao.observeActiveOrders()

    /** Orders taken on [date] in the shop's time zone, newest first. */
    fun observeOrdersOn(date: LocalDate): Flow<List<OrderListRow>> {
        val (from, to) = dayBounds(date)
        return dao.observeOrdersBetween(from, to)
    }

    fun observeOrderDetail(orderId: Long): Flow<OrderDetail?> = combine(
        dao.observeOrder(orderId),
        dao.observeLines(orderId),
        dao.observeLineAddOns(orderId),
        dao.observePayments(orderId),
        dao.observeRefunds(orderId),
    ) { order, lines, addOns, payments, refunds ->
        order?.let {
            val addOnsByLine = addOns.groupBy { it.orderLineId }
            OrderDetail(
                order = it,
                lines = lines.map { line -> OrderLineDetail(line, addOnsByLine[line.id].orEmpty()) },
                payments = payments,
                refunds = refunds
            )
        }
    }

    // ---------- Placing an order ----------

    /**
     * Saves a paid order. Names and prices are read from the menu now, not trusted from the
     * screen. With [OrderDraft.completeNow] the order is also completed in the same transaction.
     */
    suspend fun placeOrder(draft: OrderDraft): PlacedOrder = db.withTransaction {
        if (draft.lines.isEmpty()) throw UserFacingException("Add at least one item")
        val now = clock.instant()

        // Build the lines from today's menu.
        val pricedLines = draft.lines.map { cartLine ->
            if (cartLine.quantity !in 1..MAX_QUANTITY) throw UserFacingException("Quantities must be 1 to $MAX_QUANTITY")
            val size = catalogDao.getVariation(cartLine.variationId)
            val product = size?.let { catalogDao.getProduct(it.productId) }
            if (size == null || product == null || size.isArchived || product.isArchived || size.productId != cartLine.productId) {
                throw UserFacingException("An item in the cart is no longer on the menu. Remove it and try again.")
            }
            if (!product.isAvailable) throw UserFacingException("${product.name} is marked sold out")
            val addOns = cartLine.addOns.filterValues { it > 0 }.map { (addOnId, count) ->
                val addOn = catalogDao.getAddOn(addOnId)
                if (addOn == null || addOn.isArchived || !addOn.isAvailable) {
                    throw UserFacingException("An add-on in the cart is no longer available. Edit the item and try again.")
                }
                if (count > MAX_ADD_ON_COUNT) throw UserFacingException("At most $MAX_ADD_ON_COUNT of one add-on per item")
                OrderLineAddOnEntity(orderLineId = 0, addOnId = addOn.id, addOnName = addOn.name, priceCentavos = addOn.priceCentavos, quantityPerItem = count)
            }
            val addOnsUnit = addOns.sumOf { it.priceCentavos * it.quantityPerItem }
            val sizeCount = catalogDao.getVariations(product.id).count { !it.isArchived }
            OrderLineEntity(
                orderId = 0,
                productId = product.id,
                variationId = size.id,
                productName = product.name,
                // A one-size product's "Regular" is noise on a receipt.
                variationName = if (sizeCount == 1) "" else size.name,
                unitPriceCentavos = size.priceCentavos,
                quantity = cartLine.quantity,
                addOnsUnitPriceCentavos = addOnsUnit,
                lineTotalCentavos = (size.priceCentavos + addOnsUnit) * cartLine.quantity,
                note = cartLine.note.trim()
            ) to addOns
        }

        val subtotal = pricedLines.sumOf { it.first.lineTotalCentavos }
        val discount = CartMath.discountAmount(subtotal, draft.discount)
        val discountReason = draft.discountReason.trim()
        if (discount > 0 && discountReason.isEmpty()) throw UserFacingException("Give a reason for the discount")
        if (draft.discount is Discount.Percent && draft.discount.percent !in 1..100) {
            throw UserFacingException("Discount must be 1% to 100%")
        }
        val total = subtotal - discount

        val payment = draft.payment
        val change = if (payment.method == PaymentMethod.CASH) {
            val received = payment.cashReceivedCentavos ?: throw UserFacingException("Enter the cash received")
            if (received < total) throw UserFacingException("Cash received is less than the total of ${formatPeso(total)}")
            received - total
        } else null

        val orderNumber = nextOrderNumber(now)
        val orderId = dao.insertOrder(
            OrderEntity(
                orderNumber = orderNumber,
                status = OrderStatus.PENDING,
                subtotalCentavos = subtotal,
                discountCentavos = discount,
                discountReason = if (discount > 0) discountReason else "",
                totalCentavos = total,
                note = draft.note.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
        pricedLines.forEach { (line, addOns) ->
            val lineId = dao.insertLine(line.copy(orderId = orderId))
            if (addOns.isNotEmpty()) dao.insertLineAddOns(addOns.map { it.copy(orderLineId = lineId) })
        }
        dao.insertPayment(
            PaymentEntity(
                orderId = orderId,
                method = payment.method,
                amountCentavos = total,
                cashReceivedCentavos = if (payment.method == PaymentMethod.CASH) payment.cashReceivedCentavos else null,
                changeCentavos = change,
                reference = payment.reference.trim(),
                paidAt = now
            )
        )
        if (draft.completeNow) complete(requireNotNull(dao.getOrder(orderId)))
        PlacedOrder(orderId, orderNumber, total, change)
    }

    // ---------- Moving an order along ----------

    /** Pending -> Processing: someone has started making it. */
    suspend fun startOrder(orderId: Long) = db.withTransaction {
        val order = getOrder(orderId)
        if (order.status != OrderStatus.PENDING) throw UserFacingException("Only pending orders can be started")
        dao.updateOrder(order.copy(status = OrderStatus.PROCESSING, updatedAt = clock.instant()))
    }

    /** Handed over: takes the ingredients out of stock and fixes the order's cost of goods. */
    suspend fun completeOrder(orderId: Long) = db.withTransaction {
        val order = getOrder(orderId)
        if (!order.status.isActive) throw UserFacingException("This order is already ${order.status.label.lowercase()}")
        complete(order)
    }

    /**
     * Cancels an order that has not been handed over. Its payment is given back as a refund
     * record; no stock was taken out yet, so none is returned.
     */
    suspend fun cancelOrder(orderId: Long, reason: String) = db.withTransaction {
        val order = getOrder(orderId)
        if (!order.status.isActive) throw UserFacingException("Only pending or processing orders can be cancelled")
        val cleanReason = reason.trim().ifEmpty { throw UserFacingException("Give a reason for cancelling") }
        val now = clock.instant()
        refundPayments(order, "Cancelled: $cleanReason", now)
        dao.updateOrder(order.copy(status = OrderStatus.CANCELLED, cancelledAt = now, updatedAt = now))
        audit.record(
            AuditEntityType.ORDER, order.id, AuditAction.STATUS_CHANGE,
            "Cancelled order ${order.orderNumber}", details = cleanReason
        )
    }

    /**
     * Refunds a completed order in full. When [returnStock] is true (e.g. the drink was never
     * made or can be resold) its ingredients are put back at the cost they were taken out at.
     */
    suspend fun refundOrder(orderId: Long, reason: String, returnStock: Boolean) = db.withTransaction {
        val order = getOrder(orderId)
        if (order.status != OrderStatus.COMPLETED) throw UserFacingException("Only completed orders can be refunded")
        val cleanReason = reason.trim().ifEmpty { throw UserFacingException("Give a reason for the refund") }
        val now = clock.instant()
        refundPayments(order, cleanReason, now)
        val returned = if (returnStock) inventory.returnSale(order.id, order.orderNumber) else 0L
        dao.updateOrder(order.copy(status = OrderStatus.REFUNDED, updatedAt = now))
        audit.record(
            AuditEntityType.ORDER, order.id, AuditAction.STATUS_CHANGE,
            "Refunded order ${order.orderNumber} (${formatPeso(order.totalCentavos)})",
            details = cleanReason + if (returnStock) "\nIngredients returned to stock: ${formatPeso(returned)}" else ""
        )
    }

    // ---------- Helpers ----------

    /** Takes stock out for every line and add-on, then marks the order completed. */
    private suspend fun complete(order: OrderEntity) {
        val lines = dao.getLines(order.id)
        val addOnsByLine = dao.getLineAddOns(order.id).groupBy { it.orderLineId }
        val orderUsage = mutableMapOf<Long, Long>()
        val updatedLines = lines.map { line ->
            val lineUsage = mutableMapOf<Long, Long>()
            line.variationId?.let { sizeId ->
                catalogDao.getVariationRecipe(sizeId).forEach {
                    lineUsage.merge(it.ingredientId, it.quantityMilli * line.quantity, Long::plus)
                }
            }
            addOnsByLine[line.id].orEmpty().forEach { addOn ->
                val addOnId = addOn.addOnId ?: return@forEach
                catalogDao.getAddOnRecipe(addOnId).forEach {
                    lineUsage.merge(it.ingredientId, it.quantityMilli * addOn.quantityPerItem * line.quantity, Long::plus)
                }
            }
            lineUsage.forEach { (ingredientId, milli) -> orderUsage.merge(ingredientId, milli, Long::plus) }
            // Per-line cost for product profitability; the order total below comes from the ledger itself.
            val lineCost = lineUsage.entries.sumOf { (ingredientId, milli) ->
                inventory.getIngredient(ingredientId)?.let { InventoryMath.costOf(milli, it.averageCostCentavosPerUnit) } ?: 0L
            }
            line.copy(cogsCentavos = lineCost)
        }
        val cogs = inventory.recordSale(order.id, order.orderNumber, orderUsage)
        dao.updateLines(updatedLines)
        val now = clock.instant()
        dao.updateOrder(order.copy(status = OrderStatus.COMPLETED, cogsCentavos = cogs, completedAt = now, updatedAt = now))
    }

    private suspend fun refundPayments(order: OrderEntity, reason: String, now: Instant) {
        val payments = dao.getPayments(order.id)
        val paid = payments.sumOf { it.amountCentavos }
        if (paid <= 0) return
        dao.insertRefund(
            RefundEntity(
                orderId = order.id,
                amountCentavos = paid,
                method = payments.first().method,
                reason = reason,
                refundedAt = now
            )
        )
    }

    private suspend fun getOrder(id: Long): OrderEntity = dao.getOrder(id) ?: throw UserFacingException("Order not found")

    /** Numbers restart each day: 261003-001, 261003-002, ... */
    private suspend fun nextOrderNumber(now: Instant): String {
        val today = now.atZone(clock.zone).toLocalDate()
        val (from, to) = dayBounds(today)
        val next = dao.countOrdersBetween(from, to) + 1
        return today.format(DAY_PREFIX) + "-" + next.toString().padStart(3, '0')
    }

    private fun dayBounds(date: LocalDate): Pair<Instant, Instant> =
        date.atStartOfDay(clock.zone).toInstant() to date.plusDays(1).atStartOfDay(clock.zone).toInstant()

    private companion object {
        const val MAX_QUANTITY = 99
        const val MAX_ADD_ON_COUNT = 10
        val DAY_PREFIX: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")
    }
}
