package com.project2.coffeeshoppos.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.project2.coffeeshoppos.data.local.dao.CustomerSummary
import com.project2.coffeeshoppos.data.local.entity.LoyaltyEntryEntity
import com.project2.coffeeshoppos.data.repository.LoyaltyRepository
import com.project2.coffeeshoppos.data.repository.OrderDetail
import com.project2.coffeeshoppos.data.repository.OrderRepository
import com.project2.coffeeshoppos.ui.navigation.OrderDetailRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderDetailUiState(
    val isLoading: Boolean = true,
    val detail: OrderDetail? = null,
    /** The loyalty customer, if one was attached to the order. */
    val customer: CustomerSummary? = null,
    /** Stamps this order earned or used. */
    val loyaltyEntries: List<LoyaltyEntryEntity> = emptyList(),
    val isWorking: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: OrderRepository,
    loyaltyRepository: LoyaltyRepository,
) : ViewModel() {
    private val orderId = savedStateHandle.toRoute<OrderDetailRoute>().orderId
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val detail = repository.observeOrderDetail(orderId)
    private val customer = detail.map { it?.order?.customerId }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(null) else loyaltyRepository.observeCustomer(id)
    }

    val uiState: StateFlow<OrderDetailUiState> = combine(
        detail, customer, loyaltyRepository.observeEntriesForOrder(orderId), working, message
    ) { detail, customer, entries, working, message ->
        OrderDetailUiState(
            isLoading = false,
            detail = detail,
            customer = customer,
            loyaltyEntries = entries,
            isWorking = working,
            message = message
        )
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
