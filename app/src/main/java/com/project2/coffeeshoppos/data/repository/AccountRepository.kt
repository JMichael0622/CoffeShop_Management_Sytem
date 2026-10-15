package com.project2.coffeeshoppos.data.repository

import androidx.room.withTransaction
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.local.entity.UserEntity
import com.project2.coffeeshoppos.domain.AuditAction
import com.project2.coffeeshoppos.domain.CurrentUser
import com.project2.coffeeshoppos.domain.PinHasher
import com.project2.coffeeshoppos.domain.UserFacingException
import com.project2.coffeeshoppos.domain.UserRole
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Admin and staff accounts, signed in with a PIN.
 *
 * There is always at least one active admin once the app is set up, so the shop can never be
 * locked out of its own settings by deactivating or demoting the last one.
 */
class AccountRepository(
    private val db: CoffeeShopDatabase,
    private val audit: AuditRepository,
    private val clock: Clock,
) {
    private val dao = db.accountDao()

    /** Wrong PIN attempts per account, kept in memory only. */
    private val failures = mutableMapOf<Long, Pair<Int, Instant?>>()

    fun observeAccounts(): Flow<List<UserEntity>> = dao.observeAll()

    /** False until the first admin account is created. */
    fun observeIsSetUp(): Flow<Boolean> = dao.observeCount().map { it > 0 }

    /** The very first account, made on first launch. It is always an admin. */
    suspend fun createFirstAdmin(name: String, pin: String): Long = db.withTransaction {
        if (dao.count() > 0) throw UserFacingException("The app is already set up. Sign in instead.")
        insertAccount(name, UserRole.ADMIN, pin)
    }

    suspend fun addAccount(name: String, role: UserRole, pin: String): Long = db.withTransaction {
        insertAccount(name, role, pin)
    }

    suspend fun changePin(id: Long, pin: String) = db.withTransaction {
        val user = get(id)
        PinHasher.problemWith(pin)?.let { throw UserFacingException(it) }
        val salt = PinHasher.newSalt()
        dao.update(user.copy(pinHash = PinHasher.hash(pin, salt), pinSalt = salt, updatedAt = clock.instant()))
        failures.remove(id)
        audit.record(AuditEntityType.ACCOUNT, id, AuditAction.UPDATE, "Changed the PIN for \"${user.name}\"")
    }

    /** Makes [id] an admin or staff. [actingUserId] cannot change their own role. */
    suspend fun setRole(id: Long, role: UserRole, actingUserId: Long) = db.withTransaction {
        val user = get(id)
        if (user.role == role) return@withTransaction
        if (id == actingUserId) throw UserFacingException("You can't change your own role. Ask another admin.")
        if (user.role == UserRole.ADMIN && user.isActive && dao.countActiveAdmins() <= 1) {
            throw UserFacingException("Keep at least one admin. Make someone else an admin first.")
        }
        dao.update(user.copy(role = role, updatedAt = clock.instant()))
        audit.record(AuditEntityType.ACCOUNT, id, AuditAction.UPDATE, "Made \"${user.name}\" ${role.label.lowercase()}")
    }

    /** Deactivated accounts can no longer sign in. [actingUserId] cannot deactivate themselves. */
    suspend fun setActive(id: Long, active: Boolean, actingUserId: Long) = db.withTransaction {
        val user = get(id)
        if (user.isActive == active) return@withTransaction
        if (!active && id == actingUserId) throw UserFacingException("You can't deactivate your own account")
        if (!active && user.role == UserRole.ADMIN && dao.countActiveAdmins() <= 1) {
            throw UserFacingException("Keep at least one active admin")
        }
        dao.update(user.copy(isActive = active, updatedAt = clock.instant()))
        audit.record(
            AuditEntityType.ACCOUNT, id, if (active) AuditAction.RESTORE else AuditAction.ARCHIVE,
            (if (active) "Reactivated" else "Deactivated") + " \"${user.name}\""
        )
    }

    /**
     * Checks a PIN and returns who signed in. After [MAX_FAILURES] wrong PINs the account is locked
     * for [LOCKOUT], so a PIN cannot be guessed quickly.
     */
    suspend fun checkPin(id: Long, pin: String): CurrentUser {
        val user = get(id)
        if (!user.isActive) throw UserFacingException("This account is deactivated. Ask an admin.")
        val now = clock.instant()
        val (count, lockedUntil) = failures[id] ?: (0 to null)
        if (lockedUntil != null && now < lockedUntil) {
            val seconds = Duration.between(now, lockedUntil).seconds + 1
            throw UserFacingException("Too many wrong PINs. Try again in $seconds seconds.")
        }
        if (!PinHasher.matches(pin, user.pinSalt, user.pinHash)) {
            val newCount = if (lockedUntil != null) 1 else count + 1
            failures[id] = if (newCount >= MAX_FAILURES) 0 to now.plus(LOCKOUT) else newCount to null
            throw UserFacingException(
                if (newCount >= MAX_FAILURES) "Wrong PIN. Locked for ${LOCKOUT.seconds} seconds."
                else "Wrong PIN. ${MAX_FAILURES - newCount} " + (if (MAX_FAILURES - newCount == 1) "try" else "tries") + " left."
            )
        }
        failures.remove(id)
        return CurrentUser(user.id, user.name, user.role)
    }

    // ---------- Helpers ----------

    private suspend fun insertAccount(name: String, role: UserRole, pin: String): Long {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) throw UserFacingException("Enter a name")
        if (clean.length > MAX_NAME_LENGTH) throw UserFacingException("Name is too long")
        if (dao.countNamed(clean, excludeId = 0) > 0) throw UserFacingException("There is already an account named \"$clean\"")
        PinHasher.problemWith(pin)?.let { throw UserFacingException(it) }
        val now = clock.instant()
        val salt = PinHasher.newSalt()
        val id = dao.insert(
            UserEntity(name = clean, role = role, pinHash = PinHasher.hash(pin, salt), pinSalt = salt, createdAt = now, updatedAt = now)
        )
        audit.record(AuditEntityType.ACCOUNT, id, AuditAction.CREATE, "Added ${role.label.lowercase()} account \"$clean\"")
        return id
    }

    private suspend fun get(id: Long): UserEntity = dao.get(id) ?: throw UserFacingException("Account not found")

    companion object {
        const val MAX_FAILURES = 5
        val LOCKOUT: Duration = Duration.ofSeconds(30)
        private const val MAX_NAME_LENGTH = 40
    }
}

/**
 * Who is signed in right now. Kept in memory only: closing the app, or leaving it in the
 * background for [AUTO_SIGN_OUT], signs out, so a phone left on the counter is not left open.
 */
class SessionManager(private val accounts: AccountRepository, private val clock: Clock) {
    private val _current = MutableStateFlow<CurrentUser?>(null)
    val current: StateFlow<CurrentUser?> = _current.asStateFlow()
    private var backgroundedAt: Instant? = null

    suspend fun signIn(userId: Long, pin: String) {
        _current.value = accounts.checkPin(userId, pin)
    }

    fun signOut() {
        _current.value = null
    }

    fun onAppBackgrounded() {
        backgroundedAt = clock.instant()
    }

    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (Duration.between(since, clock.instant()) >= AUTO_SIGN_OUT) signOut()
    }

    companion object {
        val AUTO_SIGN_OUT: Duration = Duration.ofMinutes(5)
    }
}
