package com.project2.coffeeshoppos.ui.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.project2.coffeeshoppos.data.local.dao.CustomerSummary
import com.project2.coffeeshoppos.data.local.entity.LoyaltyEntryEntity
import com.project2.coffeeshoppos.data.local.entity.OrderEntity
import com.project2.coffeeshoppos.data.repository.LoyaltyRepository
import com.project2.coffeeshoppos.domain.LoyaltyRules
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.ui.navigation.CustomerDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add/remove stamps dialog. */
data class AdjustStampsState(
    val add: Boolean = true,
    val countText: String = "",
    val reason: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
)

data class CustomerDetailUiState(
    val isLoading: Boolean = true,
    val summary: CustomerSummary? = null,
    val favorite: String? = null,
    val entries: List<LoyaltyEntryEntity> = emptyList(),
    val orders: List<OrderEntity> = emptyList(),
    val rules: LoyaltyRules = LoyaltyRules.DEFAULT,
    val editDialog: CustomerDialogState? = null,
    val adjustDialog: AdjustStampsState? = null,
    /** Set once the customer is deleted, so the screen closes. */
    val isDeleted: Boolean = false,
    val message: String? = null,
)

private data class Dialogs(
    val edit: CustomerDialogState? = null,
    val adjust: AdjustStampsState? = null,
    val message: String? = null,
    val deleted: Boolean = false,
)

class CustomerDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: LoyaltyRepository,
) : ViewModel() {
    private val customerId = savedStateHandle.toRoute<CustomerDetailRoute>().customerId
    private val dialogs = MutableStateFlow(Dialogs())

    val uiState: StateFlow<CustomerDetailUiState> = combine(
        combine(repository.observeCustomer(customerId), repository.observeFavoriteProduct(customerId), ::Pair),
        repository.observeEntries(customerId),
        repository.observeCustomerOrders(customerId),
        repository.observeRules(),
        dialogs,
    ) { (summary, favorite), entries, orders, rules, dialogs ->
        CustomerDetailUiState(
            isLoading = false,
            summary = summary?.takeIf { !it.customer.isDeleted },
            favorite = favorite,
            entries = entries,
            orders = orders,
            rules = rules,
            editDialog = dialogs.edit,
            adjustDialog = dialogs.adjust,
            isDeleted = dialogs.deleted,
            message = dialogs.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerDetailUiState())

    fun onMessageShown() = dialogs.update { it.copy(message = null) }

    // ---------- Edit ----------

    fun openEdit() {
        val customer = uiState.value.summary?.customer ?: return
        dialogs.update {
            it.copy(edit = CustomerDialogState(customer.id, customer.name, customer.phone.orEmpty(), customer.note))
        }
    }

    fun closeEdit() = dialogs.update { it.copy(edit = null) }
    private fun editEdit(transform: (CustomerDialogState) -> CustomerDialogState) =
        dialogs.update { it.copy(edit = it.edit?.let(transform)) }
    fun onNameChange(value: String) = editEdit { it.copy(name = value, error = null) }
    fun onPhoneChange(value: String) = editEdit { it.copy(phone = value, error = null) }
    fun onNoteChange(value: String) = editEdit { it.copy(note = value, error = null) }

    fun saveEdit() {
        val edit = dialogs.value.edit ?: return
        if (edit.isSaving) return
        editEdit { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                repository.updateCustomer(customerId, edit.name, edit.phone, edit.note)
                dialogs.update { it.copy(edit = null, message = "Customer updated") }
            } catch (e: UserFacingException) {
                editEdit { it.copy(isSaving = false, error = e.message) }
            } catch (e: Exception) {
                editEdit { it.copy(isSaving = false, error = "Could not save: ${e.message}") }
            }
        }
    }

    // ---------- Adjust stamps ----------

    fun openAdjust() = dialogs.update { it.copy(adjust = AdjustStampsState()) }
    fun closeAdjust() = dialogs.update { it.copy(adjust = null) }
    private fun editAdjust(transform: (AdjustStampsState) -> AdjustStampsState) =
        dialogs.update { it.copy(adjust = it.adjust?.let(transform)) }
    fun onAdjustDirection(add: Boolean) = editAdjust { it.copy(add = add, error = null) }
    fun onAdjustCount(value: String) = editAdjust { it.copy(countText = value.filter(Char::isDigit).take(3), error = null) }
    fun onAdjustReason(value: String) = editAdjust { it.copy(reason = value, error = null) }

    fun saveAdjust() {
        val adjust = dialogs.value.adjust ?: return
        if (adjust.isSaving) return
        val count = adjust.countText.toIntOrNull()
        if (count == null || count <= 0) {
            editAdjust { it.copy(error = "Enter how many stamps") }
            return
        }
        editAdjust { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                repository.adjustStamps(customerId, if (adjust.add) count else -count, adjust.reason)
                dialogs.update { it.copy(adjust = null, message = if (adjust.add) "Stamps added" else "Stamps removed") }
            } catch (e: UserFacingException) {
                editAdjust { it.copy(isSaving = false, error = e.message) }
            } catch (e: Exception) {
                editAdjust { it.copy(isSaving = false, error = "Could not save: ${e.message}") }
            }
        }
    }

    // ---------- Delete ----------

    fun delete() {
        viewModelScope.launch {
            runCatching { repository.deleteCustomer(customerId) }
                .onSuccess { dialogs.update { it.copy(deleted = true) } }
                .onFailure { e -> dialogs.update { it.copy(message = e.message ?: "Could not delete the customer") } }
        }
    }
}
