package com.project2.coffeeshoppos

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.repository.AuditEntityType
import com.project2.coffeeshoppos.data.repository.AuditRepository
import com.project2.coffeeshoppos.data.repository.ExpenseRepository
import com.project2.coffeeshoppos.domain.AuditAction
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.ExpenseDraft
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.PaymentStatus
import com.project2.coffeeshoppos.domain.RecurrenceFrequency
import com.project2.coffeeshoppos.domain.RecurringExpenseDraft
import com.project2.coffeeshoppos.domain.UserFacingException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the phone against a temporary in-memory database (your real data is not touched). */
@RunWith(AndroidJUnit4::class)
class ExpenseRepositoryTest {
    private lateinit var db: CoffeeShopDatabase
    private lateinit var audit: AuditRepository
    private lateinit var repository: ExpenseRepository
    private val today = LocalDate.of(2026, 10, 7)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, CoffeeShopDatabase::class.java).build()
        val clock = Clock.fixed(Instant.parse("2026-10-07T02:00:00Z"), ZoneId.of("Asia/Manila"))
        audit = AuditRepository(db, clock)
        repository = ExpenseRepository(db, audit, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun electricity(id: Long = 0, amount: Long = 350_000, paid: Boolean = true, paidDate: LocalDate? = today) = ExpenseDraft(
        id = id, description = "Electricity bill", amountCentavos = amount, category = ExpenseCategory.ELECTRICITY,
        incurredDate = today.minusDays(2), isPaid = paid, paidDate = paidDate, paymentMethod = PaymentMethod.GCASH,
        reference = "MER-1", notes = ""
    )

    private val rent = RecurringExpenseDraft(0, "Rent", 1_500_000, ExpenseCategory.RENT, RecurrenceFrequency.MONTHLY, 5, PaymentMethod.CASH, "")

    @Test
    fun recordsAndAuditsEditsWithOldValues() = runBlocking<Unit> {
        val id = repository.saveExpense(electricity())
        repository.saveExpense(electricity(id = id, amount = 360_000))

        val expense = repository.getExpense(id)!!
        assertEquals(360_000L, expense.amountCentavos)
        assertEquals(PaymentStatus.PAID, expense.paymentStatus)
        assertEquals(listOf(id), repository.observeMonth(YearMonth.of(2026, 10)).first().map { it.id })

        val edit = audit.observeFor(AuditEntityType.EXPENSE, id).first().first { it.action == AuditAction.UPDATE }
        assertTrue(edit.details.contains("₱3,500.00 → ₱3,600.00"))
    }

    @Test
    fun deleteKeepsACopyInTheAuditLog() = runBlocking<Unit> {
        val id = repository.saveExpense(electricity())
        assertThrows(UserFacingException::class.java) { runBlocking { repository.deleteExpense(id, " ") } }
        repository.deleteExpense(id, "Entered twice")

        assertNull(repository.getExpense(id))
        val record = audit.observeFor(AuditEntityType.EXPENSE, id).first().first { it.action == AuditAction.DELETE }
        assertTrue(record.details.contains("Reason: Entered twice"))
        assertTrue(record.details.contains("Amount: ₱3,500.00"))
    }

    @Test
    fun unpaidExpensesCanBeMarkedPaidButNotInTheFuture() = runBlocking<Unit> {
        val id = repository.saveExpense(electricity(paid = false))
        val unpaid = repository.getExpense(id)!!
        assertNull(unpaid.paymentMethod)
        assertEquals(listOf(id), repository.observeUnpaid().first().map { it.id })

        assertThrows(UserFacingException::class.java) {
            runBlocking { repository.markPaid(id, PaymentMethod.CASH, today.plusDays(1)) }
        }
        repository.markPaid(id, PaymentMethod.CASH, today)
        val paid = repository.getExpense(id)!!
        assertEquals(PaymentStatus.PAID, paid.paymentStatus)
        assertEquals(today, paid.paidDate)
        assertTrue(repository.observeUnpaid().first().isEmpty())
    }

    @Test
    fun recurringReminderDisappearsOnceRecordedAndCannotBeRecordedTwice() = runBlocking<Unit> {
        val templateId = repository.saveTemplate(rent)
        val reminder = repository.observeReminders().first().single()
        assertEquals("2026-10", reminder.periodKey)
        assertEquals(LocalDate.of(2026, 10, 5), reminder.dueDate)
        assertTrue(reminder.isOverdue)

        val draft = ExpenseDraft(
            0, "Rent", 1_500_000, ExpenseCategory.RENT, reminder.dueDate, true, today, PaymentMethod.CASH, "", "",
            recurringTemplateId = templateId, recurringPeriodKey = reminder.periodKey
        )
        val id = repository.saveExpense(draft)
        assertTrue(repository.observeReminders().first().isEmpty())
        assertThrows(UserFacingException::class.java) { runBlocking { repository.saveExpense(draft) } }

        // Editing the recorded one is fine and keeps its link to the template.
        repository.saveExpense(draft.copy(id = id, amountCentavos = 1_550_000))
        assertEquals("2026-10", repository.getExpense(id)!!.recurringPeriodKey)
    }

    @Test
    fun stoppedTemplatesGiveNoReminders() = runBlocking<Unit> {
        val templateId = repository.saveTemplate(rent)
        repository.setTemplateActive(templateId, false)
        assertTrue(repository.observeReminders().first().isEmpty())
        repository.setTemplateActive(templateId, true)
        assertEquals(1, repository.observeReminders().first().size)
    }

    @Test
    fun rejectsBadInput() = runBlocking<Unit> {
        assertThrows(UserFacingException::class.java) { runBlocking { repository.saveExpense(electricity(amount = 0)) } }
        assertThrows(UserFacingException::class.java) {
            runBlocking { repository.saveExpense(electricity(paidDate = today.plusDays(3))) }
        }
        assertThrows(UserFacingException::class.java) { runBlocking { repository.saveTemplate(rent.copy(dueDay = 32)) } }
        assertThrows(UserFacingException::class.java) {
            runBlocking { repository.saveTemplate(rent.copy(frequency = RecurrenceFrequency.WEEKLY, dueDay = 8)) }
        }
    }
}
