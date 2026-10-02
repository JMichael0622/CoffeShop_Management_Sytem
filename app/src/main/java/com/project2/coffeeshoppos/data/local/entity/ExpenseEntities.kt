package com.project2.coffeeshoppos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.project2.coffeeshoppos.domain.AuditAction
import com.project2.coffeeshoppos.domain.ExpenseCategory
import com.project2.coffeeshoppos.domain.PaymentMethod
import com.project2.coffeeshoppos.domain.PaymentStatus
import com.project2.coffeeshoppos.domain.RecurrenceFrequency
import java.time.Instant
import java.time.LocalDate

/** An operating expense (rent, electricity...). Stock purchases are NOT expenses; see purchases. */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = RecurringExpenseTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringTemplateId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("incurredDate"),
        Index("category"),
        // Stops the same recurring expense being entered twice for the same period.
        Index(value = ["recurringTemplateId", "recurringPeriodKey"], unique = true)
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val amountCentavos: Long,
    val category: ExpenseCategory,
    /** Reports use this date by default. */
    val incurredDate: LocalDate,
    val paidDate: LocalDate? = null,
    val paymentStatus: PaymentStatus,
    val paymentMethod: PaymentMethod? = null,
    val reference: String = "",
    val notes: String = "",
    val recurringTemplateId: Long? = null,
    /** e.g. "2026-10" for a monthly template or "2026-W40" for a weekly one. */
    val recurringPeriodKey: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** A reminder for a regular expense. It never creates a PAID expense by itself. */
@Entity(tableName = "recurring_expense_templates")
data class RecurringExpenseTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val amountCentavos: Long,
    val category: ExpenseCategory,
    val frequency: RecurrenceFrequency,
    /** Day of month (1-31) for MONTHLY, or day of week (1 = Monday) for WEEKLY. */
    val dueDay: Int,
    val paymentMethod: PaymentMethod? = null,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Instant,
)

/** A permanent log of important changes, e.g. an expense edited or deleted, or an order cancelled. */
@Entity(
    tableName = "audit_records",
    indices = [Index(value = ["entityType", "entityId"]), Index("createdAt")]
)
data class AuditRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: Long,
    val action: AuditAction,
    val summary: String,
    /** Optional text copy of the record before/after the change. */
    val details: String = "",
    val createdAt: Instant,
)
