package com.project2.coffeeshoppos.ui.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.project2.coffeeshoppos.data.repository.ExpenseRepository
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.ExpenseDraft
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.PaymentStatus
import com.project2.coffeeshoppos.domain.Recurrence
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.ui.navigation.ExpenseEditRoute
import com.project2.coffeeshoppos.util.centavosToInput
import com.project2.coffeeshoppos.util.parsePesoToCentavos
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExpenseForm(
    val description: String = "",
    val amountText: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val incurredDate: LocalDate = LocalDate.MIN,
    val isPaid: Boolean = true,
    val paidDate: LocalDate = LocalDate.MIN,
    val method: PaymentMethod = PaymentMethod.CASH,
    val reference: String = "",
    val notes: String = "",
)

data class ExpenseFormErrors(val description: String? = null, val amount: String? = null) {
    val hasErrors: Boolean get() = description != null || amount != null
}

data class ExpenseEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val form: ExpenseForm = ExpenseForm(),
    val savedForm: ExpenseForm = ExpenseForm(),
    val today: LocalDate = LocalDate.MIN,
    /** e.g. "Rent · 2026-10" when recording or editing a recurring expense. */
    val recurringLabel: String? = null,
    val errors: ExpenseFormErrors = ExpenseFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val isFinished: Boolean = false,
) {
    val hasUnsavedChanges: Boolean get() = form != savedForm
}

class ExpenseEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ExpenseRepository,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ExpenseEditRoute>()
    private var templateId: Long? = null
    private var periodKey: String? = null
    private var showErrors = false

    private val _uiState = MutableStateFlow(ExpenseEditUiState(isNew = route.expenseId == 0L))
    val uiState: StateFlow<ExpenseEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val today = LocalDate.now(clock)
        val form: ExpenseForm
        var recurringLabel: String? = null
        if (route.expenseId != 0L) {
            val expense = repository.getExpense(route.expenseId)
            if (expense == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true, today = today) }
                return
            }
            form = ExpenseForm(
                description = expense.description,
                amountText = centavosToInput(expense.amountCentavos),
                category = expense.category,
                incurredDate = expense.incurredDate,
                isPaid = expense.paymentStatus == PaymentStatus.PAID,
                paidDate = expense.paidDate ?: today,
                method = expense.paymentMethod ?: PaymentMethod.CASH,
                reference = expense.reference,
                notes = expense.notes
            )
            recurringLabel = expense.recurringPeriodKey?.let { "Recurring · $it" }
        } else if (route.templateId != 0L) {
            val template = repository.getTemplate(route.templateId)
            if (template == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true, today = today) }
                return
            }
            templateId = template.id
            periodKey = Recurrence.periodKey(template.frequency, today)
            form = ExpenseForm(
                description = template.description,
                amountText = centavosToInput(template.amountCentavos),
                category = template.category,
                incurredDate = Recurrence.dueDate(template.frequency, template.dueDay, today),
                paidDate = today,
                method = template.paymentMethod ?: PaymentMethod.CASH,
                notes = template.notes
            )
            recurringLabel = "Recurring · $periodKey"
        } else {
            form = ExpenseForm(incurredDate = today, paidDate = today)
        }
        _uiState.update {
            it.copy(isLoading = false, form = form, savedForm = form, today = today, recurringLabel = recurringLabel)
        }
    }

    private fun updateForm(transform: (ExpenseForm) -> ExpenseForm) = _uiState.update { state ->
        val form = transform(state.form)
        state.copy(form = form, errors = if (showErrors) validate(form) else ExpenseFormErrors())
    }

    fun onDescription(value: String) = updateForm { it.copy(description = value) }
    fun onAmount(value: String) = updateForm { it.copy(amountText = value) }
    fun onCategory(value: ExpenseCategory) = updateForm { it.copy(category = value) }
    fun onIncurredDate(value: LocalDate) = updateForm { it.copy(incurredDate = value) }
    fun onPaid(value: Boolean) = updateForm { it.copy(isPaid = value) }
    fun onPaidDate(value: LocalDate) = updateForm { it.copy(paidDate = value) }
    fun onMethod(value: PaymentMethod) = updateForm { it.copy(method = value) }
    fun onReference(value: String) = updateForm { it.copy(reference = value) }
    fun onNotes(value: String) = updateForm { it.copy(notes = value) }
    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun validate(form: ExpenseForm): ExpenseFormErrors {
        val amount = parsePesoToCentavos(form.amountText)
        return ExpenseFormErrors(
            description = if (form.description.isBlank()) "Enter what it was for, e.g. Electricity bill" else null,
            amount = when {
                amount == null -> "Enter a valid amount, e.g. 2500 or 2,499.50"
                amount <= 0 -> "Enter an amount above zero"
                else -> null
            }
        )
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        showErrors = true
        val errors = validate(state.form)
        if (errors.hasErrors) {
            _uiState.update { it.copy(errors = errors, message = "Please fix the highlighted fields") }
            return
        }
        val form = state.form
        _uiState.update { it.copy(isSaving = true, errors = errors) }
        viewModelScope.launch {
            try {
                repository.saveExpense(
                    ExpenseDraft(
                        id = route.expenseId,
                        description = form.description,
                        amountCentavos = requireNotNull(parsePesoToCentavos(form.amountText)),
                        category = form.category,
                        incurredDate = form.incurredDate,
                        isPaid = form.isPaid,
                        paidDate = form.paidDate,
                        paymentMethod = form.method,
                        reference = form.reference,
                        notes = form.notes,
                        recurringTemplateId = templateId,
                        recurringPeriodKey = periodKey
                    )
                )
                _uiState.update { it.copy(isSaving = false, savedForm = it.form, isFinished = true) }
            } catch (e: UserFacingException) {
                _uiState.update { it.copy(isSaving = false, message = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, message = "Could not save: ${e.message}") }
            }
        }
    }

    fun delete(reason: String) {
        viewModelScope.launch {
            try {
                repository.deleteExpense(route.expenseId, reason)
                _uiState.update { it.copy(savedForm = it.form, isFinished = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = e.message ?: "Could not delete") }
            }
        }
    }
}
