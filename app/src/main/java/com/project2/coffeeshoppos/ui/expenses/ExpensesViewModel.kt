package com.project2.coffeeshoppos.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.local.entity.ExpenseEntity
import com.project2.coffeeshoppos.data.repository.ExpenseRepository
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.ExpenseReminder
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.PaymentStatus
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryTotal(val category: ExpenseCategory, val amountCentavos: Long)

/** The "mark as paid" dialog for one unpaid expense. */
data class MarkPaidState(val expense: ExpenseEntity, val method: PaymentMethod, val paidDate: LocalDate)

data class ExpensesUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.of(2000, 1),
    val currentMonth: YearMonth = YearMonth.of(2000, 1),
    val today: LocalDate = LocalDate.MIN,
    /** When true the list shows every unpaid expense from any month instead of the chosen month. */
    val unpaidOnly: Boolean = false,
    val monthExpenses: List<ExpenseEntity> = emptyList(),
    val unpaid: List<ExpenseEntity> = emptyList(),
    val reminders: List<ExpenseReminder> = emptyList(),
    val markPaid: MarkPaidState? = null,
    val message: String? = null,
) {
    val listed: List<ExpenseEntity> get() = if (unpaidOnly) unpaid else monthExpenses
    val monthTotalCentavos: Long get() = monthExpenses.sumOf { it.amountCentavos }
    val monthUnpaidCentavos: Long get() = monthExpenses.filter { it.paymentStatus == PaymentStatus.UNPAID }.sumOf { it.amountCentavos }
    val unpaidTotalCentavos: Long get() = unpaid.sumOf { it.amountCentavos }

    /** Biggest categories first. */
    val byCategory: List<CategoryTotal>
        get() = monthExpenses.groupBy { it.category }
            .map { (category, items) -> CategoryTotal(category, items.sumOf { it.amountCentavos }) }
            .sortedByDescending { it.amountCentavos }
}

private data class ExpensesSession(
    val month: YearMonth,
    val unpaidOnly: Boolean = false,
    val markPaid: MarkPaidState? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(
    private val repository: ExpenseRepository,
    private val clock: Clock,
) : ViewModel() {
    private val session = MutableStateFlow(ExpensesSession(YearMonth.now(clock)))

    val uiState: StateFlow<ExpensesUiState> = combine(
        session.flatMapLatest { repository.observeMonth(it.month) },
        repository.observeUnpaid(),
        repository.observeReminders(),
        session,
    ) { monthExpenses, unpaid, reminders, session ->
        ExpensesUiState(
            isLoading = false,
            month = session.month,
            currentMonth = YearMonth.now(clock),
            today = LocalDate.now(clock),
            unpaidOnly = session.unpaidOnly,
            monthExpenses = monthExpenses,
            unpaid = unpaid,
            reminders = reminders,
            markPaid = session.markPaid,
            message = session.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    fun onMonth(month: YearMonth) = session.update { it.copy(month = month) }
    fun onUnpaidOnly(value: Boolean) = session.update { it.copy(unpaidOnly = value) }
    fun onMessageShown() = session.update { it.copy(message = null) }

    fun openMarkPaid(expense: ExpenseEntity) = session.update {
        it.copy(markPaid = MarkPaidState(expense, expense.paymentMethod ?: PaymentMethod.CASH, LocalDate.now(clock)))
    }
    fun onMarkPaidMethod(method: PaymentMethod) = session.update { it.copy(markPaid = it.markPaid?.copy(method = method)) }
    fun onMarkPaidDate(date: LocalDate) = session.update { it.copy(markPaid = it.markPaid?.copy(paidDate = date)) }
    fun closeMarkPaid() = session.update { it.copy(markPaid = null) }

    fun confirmMarkPaid() {
        val state = session.value.markPaid ?: return
        viewModelScope.launch {
            runCatching { repository.markPaid(state.expense.id, state.method, state.paidDate) }
                .onSuccess { session.update { it.copy(markPaid = null, message = "\"${state.expense.description}\" marked paid") } }
                .onFailure { e -> session.update { it.copy(message = e.message ?: "Could not update the expense") } }
        }
    }
}
