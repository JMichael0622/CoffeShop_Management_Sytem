package com.project2.coffeeshoppos.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.entity.UserEntity
import com.project2.coffeeshoppos.data.repository.AccountRepository
import com.project2.coffeeshoppos.data.repository.SessionManager
import com.project2.coffeeshoppos.domain.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add-account dialog ([account] null) or the dialog for an existing account. */
data class AccountDialogState(
    val account: UserEntity? = null,
    val name: String = "",
    val role: UserRole = UserRole.STAFF,
    val pin: String = "",
    val confirmPin: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
)

data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<UserEntity> = emptyList(),
    val currentUserId: Long = 0,
    val dialog: AccountDialogState? = null,
    val message: String? = null,
)

class AccountsViewModel(
    private val repository: AccountRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val dialog = MutableStateFlow<AccountDialogState?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val me get() = session.current.value?.id ?: 0

    val uiState: StateFlow<AccountsUiState> = combine(
        repository.observeAccounts(), session.current, dialog, message
    ) { accounts, current, dialog, message ->
        AccountsUiState(false, accounts, current?.id ?: 0, dialog, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun onMessageShown() { message.value = null }
    fun openAdd() { dialog.value = AccountDialogState() }
    fun openAccount(account: UserEntity) { dialog.value = AccountDialogState(account = account, name = account.name, role = account.role) }
    fun closeDialog() { dialog.value = null }

    private fun edit(transform: (AccountDialogState) -> AccountDialogState) =
        dialog.update { it?.let(transform)?.copy(error = null) }

    fun onName(value: String) = edit { it.copy(name = value) }
    fun onRole(value: UserRole) = edit { it.copy(role = value) }
    fun onPin(value: String) = edit { it.copy(pin = value.filter(Char::isDigit).take(6)) }
    fun onConfirmPin(value: String) = edit { it.copy(confirmPin = value.filter(Char::isDigit).take(6)) }

    /** Adds a new account, or for an existing one saves a new PIN (if typed) and role. */
    fun save() {
        val current = dialog.value ?: return
        if (current.isSaving) return
        val existing = current.account
        val changingPin = existing == null || current.pin.isNotEmpty()
        if (changingPin && current.pin != current.confirmPin) {
            dialog.value = current.copy(error = "The two PINs don't match")
            return
        }
        act(current) {
            if (existing == null) {
                repository.addAccount(current.name, current.role, current.pin)
                "Account \"${current.name.trim()}\" added"
            } else {
                if (changingPin) repository.changePin(existing.id, current.pin)
                repository.setRole(existing.id, current.role, actingUserId = me)
                "Account \"${existing.name}\" saved"
            }
        }
    }

    fun setActive(active: Boolean) {
        val current = dialog.value ?: return
        val account = current.account ?: return
        act(current) {
            repository.setActive(account.id, active, actingUserId = me)
            "\"${account.name}\" " + if (active) "can sign in again" else "can no longer sign in"
        }
    }

    private fun act(current: AccountDialogState, block: suspend () -> String) {
        dialog.value = current.copy(isSaving = true)
        viewModelScope.launch {
            try {
                message.value = block()
                dialog.value = null
            } catch (e: Exception) {
                dialog.update { it?.copy(isSaving = false, error = e.message ?: "Could not save") }
            }
        }
    }
}
