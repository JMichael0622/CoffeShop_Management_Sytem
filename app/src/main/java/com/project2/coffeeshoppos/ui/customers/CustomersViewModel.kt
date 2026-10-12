package com.project2.coffeeshoppos.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.dao.CustomerSummary
import com.project2.coffeeshoppos.data.repository.LoyaltyRepository
import com.project2.coffeeshoppos.domain.LoyaltyRules
import com.project2.coffeeshoppos.domain.UserFacingException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add/edit customer dialog. [customerId] is 0 when adding. */
data class CustomerDialogState(
    val customerId: Long = 0,
    val name: String = "",
    val phone: String = "",
    val note: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
)

data class CustomersUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val customers: List<CustomerSummary> = emptyList(),
    val rules: LoyaltyRules = LoyaltyRules.DEFAULT,
    val dialog: CustomerDialogState? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CustomersViewModel(private val repository: LoyaltyRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val dialog = MutableStateFlow<CustomerDialogState?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CustomersUiState> = combine(
        query,
        query.flatMapLatest { repository.searchCustomers(it, limit = 200) },
        repository.observeRules(),
        dialog,
        message,
    ) { query, customers, rules, dialog, message ->
        CustomersUiState(false, query, customers, rules, dialog, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomersUiState())

    fun onQueryChange(value: String) { query.value = value }
    fun onMessageShown() { message.value = null }

    fun openAdd() { dialog.value = CustomerDialogState() }
    fun closeDialog() { dialog.value = null }
    fun onNameChange(value: String) = dialog.update { it?.copy(name = value, error = null) }
    fun onPhoneChange(value: String) = dialog.update { it?.copy(phone = value, error = null) }
    fun onNoteChange(value: String) = dialog.update { it?.copy(note = value, error = null) }

    fun saveDialog() {
        val current = dialog.value ?: return
        if (current.isSaving) return
        dialog.value = current.copy(isSaving = true)
        viewModelScope.launch {
            try {
                repository.addCustomer(current.name, current.phone, current.note)
                dialog.value = null
                message.value = "Customer added"
            } catch (e: UserFacingException) {
                dialog.update { it?.copy(isSaving = false, error = e.message) }
            } catch (e: Exception) {
                dialog.update { it?.copy(isSaving = false, error = "Could not save: ${e.message}") }
            }
        }
    }
}
