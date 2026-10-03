package com.project2.coffeeshoppos.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.project2.coffeeshoppos.data.repository.OrderDetail
import com.project2.coffeeshoppos.data.repository.OrderRepository
import com.project2.coffeeshoppos.ui.navigation.OrderDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderDetailUiState(
    val isLoading: Boolean = true,
    val detail: OrderDetail? = null,
    val isWorking: Boolean = false,
    val message: String? = null,
)

class OrderDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: OrderRepository,
) : ViewModel() {
    private val orderId = savedStateHandle.toRoute<OrderDetailRoute>().orderId
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OrderDetailUiState> = combine(
        repository.observeOrderDetail(orderId), working, message
    ) { detail, working, message ->
        OrderDetailUiState(isLoading = false, detail = detail, isWorking = working, message = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderDetailUiState())

    fun onMessageShown() { message.value = null }

    fun start() = act(null) { repository.startOrder(orderId) }
    fun complete() = act("Order completed. Ingredients were taken out of stock.") { repository.completeOrder(orderId) }
    fun cancel(reason: String) = act("Order cancelled and payment refunded") { repository.cancelOrder(orderId, reason) }
    fun refund(reason: String, returnStock: Boolean) =
        act("Order refunded" + if (returnStock) "; ingredients returned to stock" else "") {
            repository.refundOrder(orderId, reason, returnStock)
        }

    private fun act(success: String?, block: suspend () -> Unit) {
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { if (success != null) message.value = success }
                .onFailure { message.value = it.message ?: "Could not update the order" }
            working.update { false }
        }
    }
}
