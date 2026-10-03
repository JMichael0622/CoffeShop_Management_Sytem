package com.project2.coffeeshoppos.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.dao.OrderListRow
import com.project2.coffeeshoppos.data.repository.OrderRepository
import com.project2.coffeeshoppos.domain.OrderStatus
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OrdersTab { ACTIVE, HISTORY }

data class OrdersUiState(
    val isLoading: Boolean = true,
    val tab: OrdersTab = OrdersTab.ACTIVE,
    val active: List<OrderListRow> = emptyList(),
    val date: LocalDate = LocalDate.MIN,
    val today: LocalDate = LocalDate.MIN,
    val dayOrders: List<OrderListRow> = emptyList(),
    val message: String? = null,
) {
    /** Sales that count for the day: completed orders, before any refunds. */
    val daySalesCentavos: Long
        get() = dayOrders.filter { it.order.status == OrderStatus.COMPLETED || it.order.status == OrderStatus.REFUNDED }
            .sumOf { it.order.totalCentavos }
    val dayCompletedCount: Int get() = dayOrders.count { it.order.status == OrderStatus.COMPLETED }
}

private data class OrdersSession(val tab: OrdersTab, val date: LocalDate, val message: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModel(
    private val repository: OrderRepository,
    private val clock: Clock,
) : ViewModel() {
    private val session = MutableStateFlow(OrdersSession(OrdersTab.ACTIVE, LocalDate.now(clock)))

    val uiState: StateFlow<OrdersUiState> = combine(
        repository.observeActiveOrders(),
        session.flatMapLatest { repository.observeOrdersOn(it.date) },
        session,
    ) { active, dayOrders, session ->
        OrdersUiState(
            isLoading = false,
            tab = session.tab,
            active = active,
            date = session.date,
            today = LocalDate.now(clock),
            dayOrders = dayOrders,
            message = session.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdersUiState())

    fun onTab(tab: OrdersTab) = session.update { it.copy(tab = tab) }
    fun onDate(date: LocalDate) = session.update { it.copy(date = minOf(date, LocalDate.now(clock))) }
    fun onMessageShown() = session.update { it.copy(message = null) }

    fun start(orderId: Long) = act { repository.startOrder(orderId) }
    fun complete(orderId: Long) = act { repository.completeOrder(orderId) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e -> session.update { it.copy(message = e.message ?: "Could not update the order") } }
        }
    }
}
