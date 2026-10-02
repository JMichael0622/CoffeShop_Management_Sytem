package com.project2.coffeeshoppos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.project2.coffeeshoppos.data.local.entity.AuditRecordEntity
import kotlinx.coroutines.flow.Flow

/** Audit records are insert-only: there is deliberately no update or delete. */
@Dao
interface AuditDao {
    @Insert
    suspend fun insert(record: AuditRecordEntity): Long

    @Query("SELECT * FROM audit_records WHERE entityType = :entityType AND entityId = :entityId ORDER BY createdAt DESC")
    fun observeFor(entityType: String, entityId: Long): Flow<List<AuditRecordEntity>>

    @Query("SELECT * FROM audit_records ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditRecordEntity>>
}
