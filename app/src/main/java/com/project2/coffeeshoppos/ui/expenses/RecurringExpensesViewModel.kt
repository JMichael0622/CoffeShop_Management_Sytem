package com.project2.coffeeshoppos.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.entity.RecurringExpenseTemplateEntity
import com.project2.coffeeshoppos.data.repository.ExpenseRepository
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.Recurrence
import com.project2.coffeeshoppos.domain.RecurrenceFrequency
import com.project2.coffeeshoppos.domain.RecurringExpenseDraft
import com.project2.coffeeshoppos.util.centavosToInput
import com.project2.coffeeshoppos.util.parsePesoToCentavos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add/edit dialog. [template] is null when adding. */
data class TemplateDialogState(
    val template: RecurringExpenseTemplateEntity?,
    val description: String = "",
    val amountText: String = "",
    val category: ExpenseCategory = ExpenseCategory.RENT,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    /** Day of month as typed (monthly). */
    val dayOfMonthText: String = "1",
    /** 1 = Monday (weekly). */
    val dayOfWeek: Int = 1,
    val method: PaymentMethod? = null,
    val notes: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
)

data class RecurringExpensesUiState(
    val isLoading: Boolean = true,
    val active: List<RecurringExpenseTemplateEntity> = emptyList(),
    val stopped: List<RecurringExpenseTemplateEntity> = emptyList(),
    val dialog: TemplateDialogState? = null,
    val message: String? = null,
)

class RecurringExpensesViewModel(private val repository: ExpenseRepository) : ViewModel() {
    private val dialog = MutableStateFlow<TemplateDialogState?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<RecurringExpensesUiState> = combine(repository.observeTemplates(), dialog, message) { templates, dialog, message ->
        RecurringExpensesUiState(
            isLoading = false,
            active = templates.filter { it.isActive },
            stopped = templates.filterNot { it.isActive },
            dialog = dialog,
            message = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringExpensesUiState())

    fun openAdd() { dialog.value = TemplateDialogState(template = null) }

    fun openEdit(template: RecurringExpenseTemplateEntity) {
        dialog.value = TemplateDialogState(
            template = template,
            description = template.description,
            amountText = centavosToInput(template.amountCentavos),
            category = template.category,
            frequency = template.frequency,
            dayOfMonthText = if (template.frequency == RecurrenceFrequency.MONTHLY) template.dueDay.toString() else "1",
            dayOfWeek = if (template.frequency == RecurrenceFrequency.WEEKLY) template.dueDay else 1,
            method = template.paymentMethod,
            notes = template.notes
        )
    }

    fun closeDialog() { dialog.value = null }
    fun onMessageShown() { message.value = null }

    private fun edit(transform: (TemplateDialogState) -> TemplateDialogState) =
        dialog.update { it?.let { current -> transform(current).copy(error = null) } }

    fun onDescription(value: String) = edit { it.copy(description = value) }
    fun onAmount(value: String) = edit { it.copy(amountText = value) }
    fun onCategory(value: ExpenseCategory) = edit { it.copy(category = value) }
    fun onFrequency(value: RecurrenceFrequency) = edit { it.copy(frequency = value) }
    fun onDayOfMonth(value: String) = edit { it.copy(dayOfMonthText = value) }
    fun onDayOfWeek(value: Int) = edit { it.copy(dayOfWeek = value) }
    /** Tapping the selected method again clears it ("any method"). */
    fun onMethod(value: PaymentMethod) = edit { it.copy(method = if (it.method == value) null else value) }
    fun onNotes(value: String) = edit { it.copy(notes = value) }

    fun save() {
        val current = dialog.value ?: return
        if (current.isSaving) return
        val amount = parsePesoToCentavos(current.amountText)
        val dueDay = when (current.frequency) {
            RecurrenceFrequency.MONTHLY -> current.dayOfMonthText.trim().toIntOrNull()
            RecurrenceFrequency.WEEKLY -> current.dayOfWeek
        }
        val error = when {
            current.description.isBlank() -> "Enter what the expense is for, e.g. Rent"
            amount == null || amount <= 0 -> "Enter the usual amount, e.g. 15000"
            dueDay == null || dueDay !in Recurrence.validDueDays(current.frequency) -> "Due day must be 1 to 31"
            else -> null
        }
        if (error != null) {
            dialog.value = current.copy(error = error)
            return
        }
        dialog.value = current.copy(isSaving = true)
        viewModelScope.launch {
            try {
                repository.saveTemplate(
                    RecurringExpenseDraft(
                        id = current.template?.id ?: 0,
                        description = current.description,
                        amountCentavos = requireNotNull(amount),
                        category = current.category,
                        frequency = current.frequency,
                        dueDay = requireNotNull(dueDay),
                        paymentMethod = current.method,
                        notes = current.notes
                    )
                )
                dialog.value = null
                if (current.template == null) message.value = "Reminder added. It shows under Expenses when due."
            } catch (e: Exception) {
                dialog.update { it?.copy(isSaving = false, error = e.message ?: "Could not save") }
            }
        }
    }

    fun setActive(template: RecurringExpenseTemplateEntity, active: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setTemplateActive(template.id, active) }
                .onSuccess {
                    dialog.value = null
                    message.value = "\"${template.description}\" ${if (active) "resumed" else "stopped"}"
                }
                .onFailure { message.value = it.message ?: "Could not update" }
        }
    }
}
