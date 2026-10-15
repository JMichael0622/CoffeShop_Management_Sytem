package com.project2.coffeeshoppos.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.project2.coffeeshoppos.domain.UserRole
import java.time.Instant

/**
 * A person who signs in to the app. Accounts are deactivated rather than deleted, so the audit
 * log can still say who did what.
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["name"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: UserRole,
    /** PBKDF2 hash of the PIN; see [com.project2.coffeeshoppos.domain.PinHasher]. */
    val pinHash: String,
    val pinSalt: String,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
)
