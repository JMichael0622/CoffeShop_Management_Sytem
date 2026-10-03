package com.project2.coffeeshoppos.data.repository

import androidx.room.withTransaction
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.local.entity.ExpenseEntity
import com.project2.coffeeshoppos.data.local.entity.RecurringExpenseTemplateEntity
import com.project2.coffeeshoppos.domain.AuditAction
import com.project2.coffeeshoppos.domain.ExpenseDraft
import com.project2.coffeeshoppos.domain.ExpenseReminder
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.PaymentStatus
import com.project2.coffeeshoppos.domain.Recurrence
import com.project2.coffeeshoppos.domain.RecurringExpenseDraft
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.domain.buildReminders
import com.project2.coffeeshoppos.util.formatPeso
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Operating expenses (rent, electricity, salaries...) and reminders for recurring ones.
 *
 * Stock purchases are not expenses; they live in the inventory. Every edit and delete of an
 * expense is written to the audit log with a copy of the old values, because expenses feed
 * straight into profit.
 */
class ExpenseRepository(
    private val db: CoffeeShopDatabase,
    private val audit: AuditRepository,
    private val clock: Clock,
) {
    private val dao = db.expenseDao()

    // ---------- Reading ----------

    fun observeExpenseCount(): Flow<Int> = dao.observeExpenseCount()
    fun observeExpenses(from: LocalDate, to: LocalDate): Flow<List<ExpenseEntity>> = dao.observeExpenses(from, to)
    fun observeMonth(month: YearMonth): Flow<List<ExpenseEntity>> = dao.observeExpenses(month.atDay(1), month.atEndOfMonth())
    fun observeUnpaid(): Flow<List<ExpenseEntity>> = dao.observeUnpaidExpenses()
    fun observeTemplates(): Flow<List<RecurringExpenseTemplateEntity>> = dao.observeTemplates()

    suspend fun getExpense(id: Long): ExpenseEntity? = dao.getExpense(id)
    suspend fun getTemplate(id: Long): RecurringExpenseTemplateEntity? = dao.getTemplate(id)

    /** Recurring expenses still to be recorded for the current week or month. */
    fun observeReminders(): Flow<List<ExpenseReminder>> = combine(
        dao.observeTemplates(), dao.observeRecordedPeriods()
    ) { templates, recorded ->
        buildReminders(
            templates,
            recorded.map { it.recurringTemplateId to it.recurringPeriodKey }.toSet(),
            LocalDate.now(clock)
        )
    }

    // ---------- Expenses ----------

    /** Creates or updates an expense. Returns its id. */
    suspend fun saveExpense(draft: ExpenseDraft): Long = db.withTransaction {
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val description = cleanText(draft.description, "Enter what the expense was for")
        if (draft.amountCentavos <= 0) throw UserFacingException("Enter an amount above zero")
        val paidDate = if (draft.isPaid) draft.paidDate ?: today else null
        if (paidDate != null && paidDate.isAfter(today)) throw UserFacingException("The paid date cannot be in the future")

        val templateId = draft.recurringTemplateId
        val periodKey = draft.recurringPeriodKey
        if (templateId != null && periodKey != null && dao.countRecordedPeriod(templateId, periodKey, excludeId = draft.id) > 0) {
            throw UserFacingException("This recurring expense is already recorded for $periodKey")
        }

        val existing = if (draft.id == 0L) null else dao.getExpense(draft.id) ?: throw UserFacingException("Expense not found")
        val entity = ExpenseEntity(
            id = existing?.id ?: 0,
            description = description,
            amountCentavos = draft.amountCentavos,
            category = draft.category,
            incurredDate = draft.incurredDate,
            paidDate = paidDate,
            paymentStatus = if (draft.isPaid) PaymentStatus.PAID else PaymentStatus.UNPAID,
            paymentMethod = if (draft.isPaid) draft.paymentMethod else null,
            reference = if (draft.isPaid) draft.reference.trim() else "",
            notes = draft.notes.trim(),
            recurringTemplateId = existing?.recurringTemplateId ?: templateId,
            recurringPeriodKey = existing?.recurringPeriodKey ?: periodKey,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        if (existing == null) {
            val id = dao.insertExpense(entity)
            audit.record(
                AuditEntityType.EXPENSE, id, AuditAction.CREATE,
                "Recorded expense \"$description\" for ${formatPeso(draft.amountCentavos)}"
            )
            id
        } else {
            val changes = describeChanges(existing, entity)
            if (changes.isNotEmpty()) {
                dao.updateExpense(entity)
                audit.record(
                    AuditEntityType.EXPENSE, existing.id, AuditAction.UPDATE,
                    "Edited expense \"$description\"", details = changes.joinToString("\n")
                )
            }
            existing.id
        }
    }

    /** Marks an unpaid expense as paid on [paidDate]. */
    suspend fun markPaid(id: Long, method: PaymentMethod, paidDate: LocalDate) = db.withTransaction {
        val expense = dao.getExpense(id) ?: throw UserFacingException("Expense not found")
        if (expense.paymentStatus == PaymentStatus.PAID) return@withTransaction
        if (paidDate.isAfter(LocalDate.now(clock))) throw UserFacingException("The paid date cannot be in the future")
        dao.updateExpense(
            expense.copy(paymentStatus = PaymentStatus.PAID, paidDate = paidDate, paymentMethod = method, updatedAt = clock.instant())
        )
        audit.record(
            AuditEntityType.EXPENSE, id, AuditAction.STATUS_CHANGE,
            "Marked \"${expense.description}\" paid by ${method.label} on $paidDate"
        )
    }

    /** Deletes an expense, keeping a full copy of it in the audit log. */
    suspend fun deleteExpense(id: Long, reason: String) = db.withTransaction {
        val expense = dao.getExpense(id) ?: throw UserFacingException("Expense not found")
        val cleanReason = reason.trim().ifEmpty { throw UserFacingException("Give a reason for deleting") }
        dao.deleteExpense(expense)
        audit.record(
            AuditEntityType.EXPENSE, id, AuditAction.DELETE,
            "Deleted expense \"${expense.description}\" (${formatPeso(expense.amountCentavos)})",
            details = "Reason: $cleanReason\n" + snapshot(expense)
        )
    }

    // ---------- Recurring expenses ----------

    suspend fun saveTemplate(draft: RecurringExpenseDraft): Long = db.withTransaction {
        val description = cleanText(draft.description, "Enter what the expense is for")
        if (draft.amountCentavos <= 0) throw UserFacingException("Enter an amount above zero")
        if (draft.dueDay !in Recurrence.validDueDays(draft.frequency)) throw UserFacingException("Choose a valid due day")
        if (draft.id == 0L) {
            val id = dao.insertTemplate(
                RecurringExpenseTemplateEntity(
                    description = description,
                    amountCentavos = draft.amountCentavos,
                    category = draft.category,
                    frequency = draft.frequency,
                    dueDay = draft.dueDay,
                    paymentMethod = draft.paymentMethod,
                    notes = draft.notes.trim(),
                    createdAt = clock.instant()
                )
            )
            audit.record(AuditEntityType.RECURRING_EXPENSE, id, AuditAction.CREATE, "Created recurring expense \"$description\"")
            id
        } else {
            val current = dao.getTemplate(draft.id) ?: throw UserFacingException("Recurring expense not found")
            val updated = current.copy(
                description = description,
                amountCentavos = draft.amountCentavos,
                category = draft.category,
                frequency = draft.frequency,
                dueDay = draft.dueDay,
                paymentMethod = draft.paymentMethod,
                notes = draft.notes.trim()
            )
            if (updated != current) {
                dao.updateTemplate(updated)
                audit.record(AuditEntityType.RECURRING_EXPENSE, current.id, AuditAction.UPDATE, "Updated recurring expense \"$description\"")
            }
            current.id
        }
    }

    /** Stops (or resumes) reminders. Expenses already recorded from it are kept. */
    suspend fun setTemplateActive(id: Long, active: Boolean) = db.withTransaction {
        val template = dao.getTemplate(id) ?: throw UserFacingException("Recurring expense not found")
        if (template.isActive == active) return@withTransaction
        dao.updateTemplate(template.copy(isActive = active))
        audit.record(
            AuditEntityType.RECURRING_EXPENSE, id,
            if (active) AuditAction.RESTORE else AuditAction.ARCHIVE,
            "${if (active) "Resumed" else "Stopped"} recurring expense \"${template.description}\""
        )
    }

    // ---------- Helpers ----------

    private fun describeChanges(old: ExpenseEntity, new: ExpenseEntity): List<String> = buildList {
        if (old.description != new.description) add("Description: \"${old.description}\" → \"${new.description}\"")
        if (old.amountCentavos != new.amountCentavos) add("Amount: ${formatPeso(old.amountCentavos)} → ${formatPeso(new.amountCentavos)}")
        if (old.category != new.category) add("Category: ${old.category.label} → ${new.category.label}")
        if (old.incurredDate != new.incurredDate) add("Date: ${old.incurredDate} → ${new.incurredDate}")
        if (old.paymentStatus != new.paymentStatus) add("Status: ${old.paymentStatus.label} → ${new.paymentStatus.label}")
        if (old.paidDate != new.paidDate && new.paidDate != null && old.paymentStatus == new.paymentStatus) {
            add("Paid date: ${old.paidDate} → ${new.paidDate}")
        }
        if (old.paymentMethod != new.paymentMethod) add("Method: ${old.paymentMethod?.label ?: "none"} → ${new.paymentMethod?.label ?: "none"}")
        if (old.reference != new.reference) add("Reference: \"${old.reference}\" → \"${new.reference}\"")
        if (old.notes != new.notes) add("Notes changed")
    }

    private fun snapshot(expense: ExpenseEntity): String = listOf(
        "Description: ${expense.description}",
        "Amount: ${formatPeso(expense.amountCentavos)}",
        "Category: ${expense.category.label}",
        "Date: ${expense.incurredDate}",
        "Status: ${expense.paymentStatus.label}" + (expense.paidDate?.let { " on $it" } ?: ""),
        "Method: ${expense.paymentMethod?.label ?: "none"}",
        "Reference: ${expense.reference}",
        "Notes: ${expense.notes}",
    ).joinToString("\n")

    private fun cleanText(text: String, emptyMessage: String): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) throw UserFacingException(emptyMessage)
        if (clean.length > MAX_DESCRIPTION_LENGTH) throw UserFacingException("Keep it under $MAX_DESCRIPTION_LENGTH characters")
        return clean
    }

    private companion object {
        const val MAX_DESCRIPTION_LENGTH = 80
    }
}
