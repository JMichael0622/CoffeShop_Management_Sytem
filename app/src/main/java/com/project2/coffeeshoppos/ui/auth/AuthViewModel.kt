package com.project2.coffeeshoppos.ui.auth

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.entity.UserEntity
import com.project2.coffeeshoppos.data.repository.AccountRepository
import com.project2.coffeeshoppos.data.repository.SessionManager
import com.project2.coffeeshoppos.domain.CurrentUser
import com.project2.coffeeshoppos.domain.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Whoever is signed in. Screens check `LocalCurrentUser.current.can(Permission.X)` to hide what
 * this person may not do. The default is only used by @Preview functions.
 */
val LocalCurrentUser = staticCompositionLocalOf { CurrentUser(0, "Preview", UserRole.ADMIN) }

/** The sign-in form, and the "create the admin account" form on first launch. */
data class AuthForm(
    val selectedId: Long? = null,
    val name: String = "",
    val pin: String = "",
    val confirmPin: String = "",
    val error: String? = null,
    val isWorking: Boolean = false,
)

data class AuthUiState(
    val isLoading: Boolean = true,
    val isSetUp: Boolean = false,
    /** Active accounts only, shown on the sign-in screen. */
    val accounts: List<UserEntity> = emptyList(),
    val currentUser: CurrentUser? = null,
    val form: AuthForm = AuthForm(),
)

class AuthViewModel(
    private val accounts: AccountRepository,
    private val session: SessionManager,
) : ViewModel() {
    private val form = MutableStateFlow(AuthForm())

    val uiState: StateFlow<AuthUiState> = combine(
        accounts.observeIsSetUp(), accounts.observeAccounts(), session.current, form
    ) { isSetUp, all, current, form ->
        val active = all.filter { it.isActive }
        AuthUiState(
            isLoading = false,
            isSetUp = isSetUp,
            accounts = active,
            currentUser = current,
            // With a single account there is nothing to choose.
            form = if (form.selectedId == null && active.size == 1) form.copy(selectedId = active.single().id) else form
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState())

    private fun edit(transform: (AuthForm) -> AuthForm) = form.update { transform(it).copy(error = null) }

    fun onSelect(id: Long) = edit { it.copy(selectedId = id, pin = "") }
    fun onName(value: String) = edit { it.copy(name = value) }
    fun onPin(value: String) = edit { it.copy(pin = value.filter(Char::isDigit).take(6)) }
    fun onConfirmPin(value: String) = edit { it.copy(confirmPin = value.filter(Char::isDigit).take(6)) }

    fun signIn() {
        val id = uiState.value.form.selectedId ?: return form.update { it.copy(error = "Choose your name") }
        run { session.signIn(id, form.value.pin) }
    }

    /** First launch: creates the admin account and signs straight in. */
    fun createAdmin() {
        val current = form.value
        if (current.pin != current.confirmPin) return form.update { it.copy(error = "The two PINs don't match") }
        run {
            val id = accounts.createFirstAdmin(current.name, current.pin)
            session.signIn(id, current.pin)
        }
    }

    private fun run(block: suspend () -> Unit) {
        if (form.value.isWorking) return
        form.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                form.value = AuthForm()
            } catch (e: Exception) {
                form.update { it.copy(isWorking = false, pin = "", error = e.message ?: "Could not sign in") }
            }
        }
    }

    fun signOut() {
        form.value = AuthForm()
        session.signOut()
    }
}
