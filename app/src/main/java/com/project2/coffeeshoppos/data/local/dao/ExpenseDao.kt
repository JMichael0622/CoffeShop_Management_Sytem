package com.project2.coffeeshoppos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.project2.coffeeshoppos.data.local.entity.ExpenseEntity
import com.project2.coffeeshoppos.data.local.entity.RecurringExpenseTemplateEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Which recurring template has been recorded for which period. */
data class RecordedPeriod(val recurringTemplateId: Long, val recurringPeriodKey: String)

@Dao
interface ExpenseDao {
    @Query(
        "SELECT * FROM expenses WHERE incurredDate BETWEEN :from AND :to " +
            "ORDER BY incurredDate DESC, id DESC"
    )
    fun observeExpenses(from: LocalDate, to: LocalDate): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpense(id: Long): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM expenses")
    fun observeExpenseCount(): Flow<Int>

    /** Unpaid expenses from any month, oldest first, so nothing owed is forgotten. */
    @Query("SELECT * FROM expenses WHERE paymentStatus = 'UNPAID' ORDER BY incurredDate, id")
    fun observeUnpaidExpenses(): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT COUNT(*) FROM expenses WHERE recurringTemplateId = :templateId " +
            "AND recurringPeriodKey = :periodKey AND id != :excludeId"
    )
    suspend fun countRecordedPeriod(templateId: Long, periodKey: String, excludeId: Long): Int

    @Query(
        "SELECT recurringTemplateId, recurringPeriodKey FROM expenses " +
            "WHERE recurringTemplateId IS NOT NULL AND recurringPeriodKey IS NOT NULL"
    )
    fun observeRecordedPeriods(): Flow<List<RecordedPeriod>>

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM recurring_expense_templates ORDER BY isActive DESC, description COLLATE NOCASE")
    fun observeTemplates(): Flow<List<RecurringExpenseTemplateEntity>>

    @Query("SELECT * FROM recurring_expense_templates WHERE id = :id")
    suspend fun getTemplate(id: Long): RecurringExpenseTemplateEntity?

    @Insert
    suspend fun insertTemplate(template: RecurringExpenseTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: RecurringExpenseTemplateEntity)
}
