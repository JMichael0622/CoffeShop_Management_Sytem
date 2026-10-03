package com.project2.coffeeshoppos.domain

import com.project2.coffeeshoppos.data.local.entity.RecurringExpenseTemplateEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** A new or edited expense. id = 0 creates one. */
data class ExpenseDraft(
    val id: Long,
    val description: String,
    val amountCentavos: Long,
    val category: ExpenseCategory,
    val incurredDate: LocalDate,
    val isPaid: Boolean,
    val paidDate: LocalDate?,
    val paymentMethod: PaymentMethod?,
    val reference: String,
    val notes: String,
    /** Set when recording a recurring expense, so the same period cannot be recorded twice. */
    val recurringTemplateId: Long? = null,
    val recurringPeriodKey: String? = null,
)

/** A new or edited recurring expense reminder. id = 0 creates one. */
data class RecurringExpenseDraft(
    val id: Long,
    val description: String,
    val amountCentavos: Long,
    val category: ExpenseCategory,
    val frequency: RecurrenceFrequency,
    /** Day of month (1-31) for monthly, day of week (1 = Monday) for weekly. */
    val dueDay: Int,
    val paymentMethod: PaymentMethod?,
    val notes: String,
)

/** A recurring expense that has not been recorded yet for the current period. */
data class ExpenseReminder(
    val templateId: Long,
    val description: String,
    val amountCentavos: Long,
    val category: ExpenseCategory,
    val periodKey: String,
    val dueDate: LocalDate,
    val isOverdue: Boolean,
)

/** Periods and due dates for recurring expenses. */
object Recurrence {
    /** "2026-10" for a monthly expense, "2026-W40" (ISO week) for a weekly one. */
    fun periodKey(frequency: RecurrenceFrequency, date: LocalDate): String = when (frequency) {
        RecurrenceFrequency.MONTHLY -> String.format(Locale.ROOT, "%04d-%02d", date.year, date.monthValue)
        RecurrenceFrequency.WEEKLY -> String.format(
            Locale.ROOT,
            "%04d-W%02d",
            date.get(IsoFields.WEEK_BASED_YEAR),
            date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        )
    }

    /**
     * The due date in the period containing [date]. A monthly due day past the end of a short
     * month falls on its last day (due on the 31st -> February 28).
     */
    fun dueDate(frequency: RecurrenceFrequency, dueDay: Int, date: LocalDate): LocalDate = when (frequency) {
        RecurrenceFrequency.MONTHLY -> date.withDayOfMonth(dueDay.coerceIn(1, date.lengthOfMonth()))
        RecurrenceFrequency.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusDays((dueDay.coerceIn(1, 7) - 1).toLong())
    }

    fun validDueDays(frequency: RecurrenceFrequency): IntRange = when (frequency) {
        RecurrenceFrequency.MONTHLY -> 1..31
        RecurrenceFrequency.WEEKLY -> 1..7
    }
}

/** Short label for a due day, e.g. "every 5th" or "every Monday". */
fun dueDayLabel(frequency: RecurrenceFrequency, dueDay: Int): String = when (frequency) {
    RecurrenceFrequency.MONTHLY -> "every ${ordinal(dueDay)}"
    RecurrenceFrequency.WEEKLY -> "every " + DayOfWeek.of(dueDay.coerceIn(1, 7)).name.lowercase().replaceFirstChar { it.uppercase() }
}

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

/**
 * Active templates not yet recorded for the period containing [today], soonest due first.
 * [recorded] holds (templateId, periodKey) pairs that already have an expense.
 */
fun buildReminders(
    templates: List<RecurringExpenseTemplateEntity>,
    recorded: Set<Pair<Long, String>>,
    today: LocalDate,
): List<ExpenseReminder> = templates
    .filter { it.isActive }
    .mapNotNull { template ->
        val key = Recurrence.periodKey(template.frequency, today)
        if (template.id to key in recorded) return@mapNotNull null
        val due = Recurrence.dueDate(template.frequency, template.dueDay, today)
        ExpenseReminder(
            templateId = template.id,
            description = template.description,
            amountCentavos = template.amountCentavos,
            category = template.category,
            periodKey = key,
            dueDate = due,
            isOverdue = due.isBefore(today)
        )
    }
    .sortedBy { it.dueDate }
