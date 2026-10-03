package com.project2.coffeeshoppos.ui.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.repository.CatalogRepository
import com.project2.coffeeshoppos.data.repository.OrderRepository
import com.project2.coffeeshoppos.domain.CartLine
import com.project2.coffeeshoppos.domain.CartMath
import com.project2.coffeeshoppos.domain.Discount
import com.project2.coffeeshoppos.domain.OrderDraft
import com.project2.coffeeshoppos.domain.PaymentInput
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.PlacedOrder
import com.project2.coffeeshoppos.domain.PosMenu
import com.project2.coffeeshoppos.domain.PosProduct
import com.project2.coffeeshoppos.domain.PricedCartLine
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.domain.priceCartLine
import com.project2.coffeeshoppos.util.centavosToInput
import com.project2.coffeeshoppos.util.parsePesoToCentavos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The item sheet: choosing size, add-ons, quantity and a note. [editingKey] is set when changing a cart line. */
data class ItemEditorState(
    val product: PosProduct,
    val editingKey: Long?,
    val sizeId: Long,
    val addOns: Map<Long, Int> = emptyMap(),
    val quantity: Int = 1,
    val note: String = "",
) {
    val totalCentavos: Long
        get() {
            val size = product.sizes.firstOrNull { it.id == sizeId }?.priceCentavos ?: 0
            val addOnsUnit = product.addOns.sumOf { (addOns[it.id] ?: 0) * it.priceCentavos }
            return (size + addOnsUnit) * quantity
        }
}

/** Everything about the sale being rung up, apart from the items. */
data class Checkout(
    val discount: Discount = Discount.None,
    val discountReason: String = "",
    val note: String = "",
    val method: PaymentMethod = PaymentMethod.CASH,
    val cashText: String = "",
    val reference: String = "",
    val completeNow: Boolean = false,
)

data class PosUiState(
    val isLoading: Boolean = true,
    val menu: PosMenu = PosMenu(emptyList(), emptyList()),
    /** null shows every category. */
    val categoryId: Long? = null,
    val cart: List<PricedCartLine> = emptyList(),
    val checkout: Checkout = Checkout(),
    val showCart: Boolean = false,
    val editor: ItemEditorState? = null,
    val activeOrderCount: Int = 0,
    val isPlacing: Boolean = false,
    val placed: PlacedOrder? = null,
    val message: String? = null,
) {
    val visibleProducts: List<PosProduct>
        get() = if (categoryId == null) menu.products else menu.products.filter { it.categoryId == categoryId }

    val itemCount: Int get() = cart.sumOf { it.line.quantity }
    val subtotalCentavos: Long get() = CartMath.subtotal(cart)
    val discountCentavos: Long get() = CartMath.discountAmount(subtotalCentavos, checkout.discount)
    val totalCentavos: Long get() = subtotalCentavos - discountCentavos
    val cashCentavos: Long? get() = parsePesoToCentavos(checkout.cashText)

    /** Change to hand back, or null while the cash typed is missing or not enough. */
    val changeCentavos: Long? get() = cashCentavos?.takeIf { it >= totalCentavos }?.minus(totalCentavos)
    val hasUnavailableItems: Boolean get() = cart.any { it.unavailableReason != null }
}

private data class Session(
    val categoryId: Long? = null,
    val lines: List<CartLine> = emptyList(),
    val checkout: Checkout = Checkout(),
    val showCart: Boolean = false,
    val editor: ItemEditorState? = null,
    val isPlacing: Boolean = false,
    val placed: PlacedOrder? = null,
    val message: String? = null,
)

class PosViewModel(
    catalogRepository: CatalogRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private val session = MutableStateFlow(Session())
    private var nextKey = 1L

    val uiState: StateFlow<PosUiState> = combine(
        catalogRepository.observePosMenu(),
        orderRepository.observeActiveOrders(),
        session,
    ) { menu, active, session ->
        PosUiState(
            isLoading = false,
            menu = menu,
            // A category that was archived or emptied falls back to "All".
            categoryId = session.categoryId?.takeIf { id -> menu.products.any { it.categoryId == id } },
            cart = session.lines.map { priceCartLine(it, menu) },
            checkout = session.checkout,
            showCart = session.showCart && session.lines.isNotEmpty(),
            editor = session.editor,
            activeOrderCount = active.size,
            isPlacing = session.isPlacing,
            placed = session.placed,
            message = session.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PosUiState())

    fun onCategoryChange(categoryId: Long?) = session.update { it.copy(categoryId = categoryId) }
    fun onMessageShown() = session.update { it.copy(message = null) }

    // ---------- Adding items ----------

    /** A product with one size and no add-ons goes straight in; anything else opens the item sheet. */
    fun onProductTap(product: PosProduct) {
        if (product.sizes.size == 1 && product.addOns.isEmpty()) {
            addToCart(CartLine(nextKey++, product.id, product.sizes.first().id, emptyMap(), quantity = 1))
            session.update { it.copy(message = "Added ${product.name}") }
        } else {
            session.update { it.copy(editor = ItemEditorState(product, editingKey = null, sizeId = product.sizes.first().id)) }
        }
    }

    fun onEditLine(key: Long) {
        val line = session.value.lines.firstOrNull { it.key == key } ?: return
        val product = uiState.value.menu.products.firstOrNull { it.id == line.productId } ?: return
        session.update {
            it.copy(editor = ItemEditorState(product, key, line.variationId, line.addOns, line.quantity, line.note))
        }
    }

    private fun editEditor(transform: (ItemEditorState) -> ItemEditorState) =
        session.update { it.copy(editor = it.editor?.let(transform)) }

    fun onEditorSize(sizeId: Long) = editEditor { it.copy(sizeId = sizeId) }
    fun onEditorQuantity(quantity: Int) = editEditor { it.copy(quantity = quantity.coerceIn(1, 99)) }
    fun onEditorNote(note: String) = editEditor { it.copy(note = note) }
    fun onEditorAddOn(addOnId: Long, count: Int) = editEditor {
        it.copy(addOns = (it.addOns + (addOnId to count.coerceIn(0, 10))).filterValues { c -> c > 0 })
    }

    fun closeEditor() = session.update { it.copy(editor = null) }

    fun confirmEditor() {
        val editor = session.value.editor ?: return
        val line = CartLine(
            key = editor.editingKey ?: nextKey++,
            productId = editor.product.id,
            variationId = editor.sizeId,
            addOns = editor.addOns,
            quantity = editor.quantity,
            note = editor.note.trim()
        )
        if (editor.editingKey == null) {
            addToCart(line)
            session.update { it.copy(editor = null) }
        } else {
            session.update { s -> s.copy(editor = null, lines = s.lines.map { if (it.key == line.key) line else it }) }
        }
    }

    /** Identical items (same size, add-ons and note) stack into one line. */
    private fun addToCart(line: CartLine) = session.update { s ->
        val same = s.lines.firstOrNull {
            it.variationId == line.variationId && it.addOns == line.addOns && it.note == line.note
        }
        val lines = if (same == null) s.lines + line
        else s.lines.map { if (it.key == same.key) it.copy(quantity = (it.quantity + line.quantity).coerceAtMost(99)) else it }
        s.copy(lines = lines)
    }

    // ---------- The cart ----------

    fun onLineQuantity(key: Long, quantity: Int) = session.update { s ->
        val lines = if (quantity <= 0) s.lines.filterNot { it.key == key }
        else s.lines.map { if (it.key == key) it.copy(quantity = quantity.coerceAtMost(99)) else it }
        s.copy(lines = lines, showCart = s.showCart && lines.isNotEmpty())
    }

    fun clearCart() = session.update { it.copy(lines = emptyList(), checkout = Checkout(), showCart = false) }
    fun openCart() = session.update { it.copy(showCart = it.lines.isNotEmpty()) }
    fun closeCart() = session.update { it.copy(showCart = false) }

    private fun editCheckout(transform: (Checkout) -> Checkout) = session.update { it.copy(checkout = transform(it.checkout)) }

    fun onDiscount(discount: Discount, reason: String) = editCheckout {
        it.copy(discount = discount, discountReason = if (discount == Discount.None) "" else reason.trim())
    }
    fun onNote(note: String) = editCheckout { it.copy(note = note) }
    fun onMethod(method: PaymentMethod) = editCheckout { it.copy(method = method) }
    fun onCashText(text: String) = editCheckout { it.copy(cashText = text) }
    fun onQuickCash(centavos: Long) = editCheckout { it.copy(cashText = centavosToInput(centavos)) }
    fun onReference(text: String) = editCheckout { it.copy(reference = text) }
    fun onCompleteNow(value: Boolean) = editCheckout { it.copy(completeNow = value) }

    fun charge() {
        val state = uiState.value
        if (state.isPlacing || state.cart.isEmpty()) return
        val checkout = state.checkout
        val problem = when {
            state.hasUnavailableItems -> "Remove the items marked unavailable first"
            checkout.method == PaymentMethod.CASH && state.cashCentavos == null -> "Enter the cash received"
            checkout.method == PaymentMethod.CASH && state.changeCentavos == null -> "Cash received is less than the total"
            state.discountCentavos > 0 && checkout.discountReason.isBlank() -> "Give a reason for the discount"
            else -> null
        }
        if (problem != null) {
            session.update { it.copy(message = problem) }
            return
        }
        session.update { it.copy(isPlacing = true) }
        viewModelScope.launch {
            try {
                val placed = orderRepository.placeOrder(
                    OrderDraft(
                        lines = session.value.lines,
                        discount = checkout.discount,
                        discountReason = checkout.discountReason,
                        note = checkout.note,
                        payment = PaymentInput(
                            method = checkout.method,
                            cashReceivedCentavos = if (checkout.method == PaymentMethod.CASH) state.cashCentavos else null,
                            reference = if (checkout.method == PaymentMethod.CASH) "" else checkout.reference
                        ),
                        completeNow = checkout.completeNow
                    )
                )
                // Start fresh for the next customer, keeping the category they were browsing.
                session.update { Session(categoryId = it.categoryId, placed = placed) }
            } catch (e: UserFacingException) {
                session.update { it.copy(isPlacing = false, message = e.message) }
            } catch (e: Exception) {
                session.update { it.copy(isPlacing = false, message = "Could not place the order: ${e.message}") }
            }
        }
    }

    fun dismissPlaced() = session.update { it.copy(placed = null) }
}
