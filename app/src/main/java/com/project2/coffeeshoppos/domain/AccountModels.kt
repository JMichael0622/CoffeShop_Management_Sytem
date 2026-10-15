package com.project2.coffeeshoppos.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Room stores these by name. Never rename a value; adding new ones is fine. */
enum class UserRole(val label: String) {
    /** The owner: can do everything. */
    ADMIN("Admin"),

    /** Counter staff: orders, inventory, customers, exports and backups. */
    STAFF("Staff"),
}

/** Things a signed-in person may be allowed to do. */
enum class Permission {
    TAKE_ORDERS,
    CANCEL_AND_REFUND,
    INVENTORY,
    CORRECT_COST,
    CUSTOMERS,
    EXPORT,
    SAVE_BACKUP,
    RESTORE_BACKUP,
    EXPENSES,
    REPORTS,
    MENU,
    LOYALTY_SETTINGS,
    ACCOUNTS,
}

object AccessRules {
    private val STAFF_PERMISSIONS = setOf(
        Permission.TAKE_ORDERS,
        Permission.INVENTORY,
        Permission.CUSTOMERS,
        Permission.EXPORT,
        Permission.SAVE_BACKUP,
    )

    fun allows(role: UserRole, permission: Permission): Boolean = when (role) {
        UserRole.ADMIN -> true
        UserRole.STAFF -> permission in STAFF_PERMISSIONS
    }
}

/** Whoever is using the app right now. */
data class CurrentUser(val id: Long, val name: String, val role: UserRole) {
    fun can(permission: Permission): Boolean = AccessRules.allows(role, permission)
}

/**
 * PINs are never stored, only a salted PBKDF2 hash, so someone reading the database file
 * cannot see them.
 */
object PinHasher {
    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    val PIN_LENGTH = 4..6

    /** Null when [pin] is fine, otherwise what is wrong with it. */
    fun problemWith(pin: String): String? = when {
        pin.length !in PIN_LENGTH || !pin.all { it.isDigit() } -> "Use a PIN of 4 to 6 digits"
        pin.toSet().size == 1 -> "Choose a PIN that isn't one repeated digit"
        pin in "0123456789" || pin in "9876543210" -> "Choose a PIN that isn't a simple sequence"
        else -> null
    }

    fun newSalt(): String = ByteArray(16).also(random::nextBytes).let { Base64.getEncoder().encodeToString(it) }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(key)
    }

    /** Compares in constant time, so how long a check takes reveals nothing about the PIN. */
    fun matches(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expectedHash.toByteArray())
}

/** How often the app reminds people to save a backup. */
enum class BackupFrequency(val label: String, val days: Long) {
    DAILY("Every day", 1),
    EVERY_3_DAYS("Every 3 days", 3),
    WEEKLY("Every week", 7),
    EVERY_2_WEEKS("Every 2 weeks", 14),
}

object BackupReminder {
    /**
     * Whether to remind about a backup: never if there is nothing to lose yet ([hasData] false),
     * always if no backup was ever saved, otherwise once the last one is older than [frequency].
     */
    fun isDue(lastBackupAt: Instant?, now: Instant, frequency: BackupFrequency, hasData: Boolean): Boolean {
        if (!hasData) return false
        if (lastBackupAt == null) return true
        return Duration.between(lastBackupAt, now) >= Duration.ofDays(frequency.days)
    }

    /** "today", "yesterday" or "9 days ago". */
    fun ageLabel(lastBackupAt: Instant, now: Instant): String = when (val days = Duration.between(lastBackupAt, now).toDays()) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }
}
