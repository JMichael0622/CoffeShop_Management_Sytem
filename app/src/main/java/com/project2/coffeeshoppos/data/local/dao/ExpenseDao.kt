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

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM recurring_expense_templates ORDER BY description")
    fun observeTemplates(): Flow<List<RecurringExpenseTemplateEntity>>

    @Insert
    suspend fun insertTemplate(template: RecurringExpenseTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: RecurringExpenseTemplateEntity)
}
