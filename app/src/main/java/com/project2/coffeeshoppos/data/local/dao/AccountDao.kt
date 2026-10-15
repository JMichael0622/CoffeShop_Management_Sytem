package com.project2.coffeeshoppos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.project2.coffeeshoppos.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun get(id: Long): UserEntity?

    /** Active accounts first, admins before staff, then by name. */
    @Query("SELECT * FROM users ORDER BY isActive DESC, role, name COLLATE NOCASE")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM users")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND isActive = 1")
    suspend fun countActiveAdmins(): Int

    /** Case-insensitive, so "Ana" and "ana" count as the same name. */
    @Query("SELECT COUNT(*) FROM users WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countNamed(name: String, excludeId: Long): Int
}
