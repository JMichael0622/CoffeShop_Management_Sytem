package com.project2.coffeeshoppos

import com.project2.coffeeshoppos.data.local.entity.RecurringExpenseTemplateEntity
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.Recurrence
import com.project2.coffeeshoppos.domain.RecurrenceFrequency
import com.project2.coffeeshoppos.domain.buildReminders
import com.project2.coffeeshoppos.domain.dueDayLabel
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceTest {
    private val monthly = RecurrenceFrequency.MONTHLY
    private val weekly = RecurrenceFrequency.WEEKLY

    private fun template(id: Long, frequency: RecurrenceFrequency, dueDay: Int, active: Boolean = true) =
        RecurringExpenseTemplateEntity(
            id = id, description = "T$id", amountCentavos = 100_000, category = ExpenseCategory.RENT,
            frequency = frequency, dueDay = dueDay, isActive = active, createdAt = Instant.EPOCH
        )

    @Test
    fun periodKeysForMonthsAndIsoWeeks() {
        assertEquals("2026-10", Recurrence.periodKey(monthly, LocalDate.of(2026, 10, 3)))
        assertEquals("2026-W40", Recurrence.periodKey(weekly, LocalDate.of(2026, 10, 3)))
        // Jan 1, 2027 still belongs to ISO week 53 of 2026.
        assertEquals("2026-W53", Recurrence.periodKey(weekly, LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun dueDatesClampToShortMonthsAndFollowWeekdays() {
        assertEquals(LocalDate.of(2026, 2, 28), Recurrence.dueDate(monthly, 31, LocalDate.of(2026, 2, 10)))
        assertEquals(LocalDate.of(2026, 10, 5), Recurrence.dueDate(monthly, 5, LocalDate.of(2026, 10, 20)))
        // Week of Monday Oct 5, 2026: Friday is Oct 9.
        assertEquals(LocalDate.of(2026, 10, 9), Recurrence.dueDate(weekly, 5, LocalDate.of(2026, 10, 7)))
        assertEquals(LocalDate.of(2026, 10, 5), Recurrence.dueDate(weekly, 1, LocalDate.of(2026, 10, 11)))
    }

    @Test
    fun remindersSkipRecordedAndStoppedTemplates() {
        val today = LocalDate.of(2026, 10, 7)
        val rent = template(1, monthly, 5)
        val wifi = template(2, monthly, 20)
        val salary = template(3, weekly, 6)
        val stopped = template(4, monthly, 1, active = false)

        val reminders = buildReminders(listOf(wifi, rent, salary, stopped), recorded = setOf(3L to "2026-W41"), today = today)

        assertEquals(listOf(1L, 2L), reminders.map { it.templateId }) // sorted by due date; salary recorded; stopped hidden
        assertTrue(reminders.first { it.templateId == 1L }.isOverdue) // due Oct 5
        assertFalse(reminders.first { it.templateId == 2L }.isOverdue) // due Oct 20
        assertEquals("2026-10", reminders.first().periodKey)
    }

    @Test
    fun dueDayLabels() {
        assertEquals("every 1st", dueDayLabel(monthly, 1))
        assertEquals("every 22nd", dueDayLabel(monthly, 22))
        assertEquals("every 13th", dueDayLabel(monthly, 13))
        assertEquals("every Friday", dueDayLabel(weekly, 5))
    }
}
