package com.project2.coffeeshoppos.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.domain.BackupFrequency
import com.project2.coffeeshoppos.domain.UserFacingException
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** What a backup file contains, shown before the owner confirms a restore. */
data class BackupInfo(
    val schemaVersion: Int,
    val products: Int,
    val ingredients: Int,
    val orders: Int,
    val expenses: Int,
    val lastOrderAt: Instant?,
)

/**
 * Saves the whole shop database to a file, and restores from one.
 *
 * A backup is a consistent snapshot made with SQLite's VACUUM INTO, so it is safe to take while
 * the app is in use. A restore is never done while the database is open: the checked file is
 * parked as a "pending restore", the app restarts, and [applyPendingRestore] swaps it in before
 * Room opens the database. A copy of the data being replaced is kept first.
 *
 * [databaseName] and [prefsName] only change in tests, so a test can never touch the real shop data.
 */
class BackupManager(
    private val context: Context,
    private val database: () -> CoffeeShopDatabase,
    private val clock: Clock,
    private val databaseName: String = CoffeeShopDatabase.FILE_NAME,
    prefsName: String = "backup_state",
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val candidate get() = File(context.cacheDir, "restore-candidate-$databaseName")
    private val pending get() = File(context.filesDir, "pending-restore-$databaseName")

    /** The data as it was just before the last restore, in case the wrong file was chosen. */
    val safetyCopy: File get() = File(File(context.filesDir, "backups"), "before-restore-$databaseName")

    val lastBackupAt: Instant? get() = prefs.getLong(KEY_LAST_BACKUP, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    private val _lastBackup = MutableStateFlow(lastBackupAt)
    /** [lastBackupAt] as a flow, so reminders disappear as soon as a backup is saved. */
    val lastBackup: StateFlow<Instant?> = _lastBackup.asStateFlow()

    private val _frequency = MutableStateFlow(
        prefs.getString(KEY_FREQUENCY, null)?.let { name -> BackupFrequency.entries.firstOrNull { it.name == name } }
            ?: BackupFrequency.WEEKLY
    )
    /** How often to remind people to save a backup. */
    val frequency: StateFlow<BackupFrequency> = _frequency.asStateFlow()

    fun setFrequency(value: BackupFrequency) {
        prefs.edit().putString(KEY_FREQUENCY, value.name).apply()
        _frequency.value = value
    }
    val lastRestoreAt: Instant? get() = prefs.getLong(KEY_LAST_RESTORE, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    fun suggestedFileName(): String = "coffeeshop-backup_${LocalDateTime.now(clock).format(FILE_STAMP)}.db"

    /** Writes a full copy of the database to [uri]. */
    suspend fun backupTo(uri: Uri) = withContext(Dispatchers.IO) {
        val snapshot = File(context.cacheDir, "backup-snapshot-$databaseName")
        try {
            snapshotTo(snapshot)
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw UserFacingException("Could not open the file to save to")
            output.use { out -> snapshot.inputStream().use { it.copyTo(out) } }
            prefs.edit().putLong(KEY_LAST_BACKUP, clock.millis()).apply()
            _lastBackup.value = lastBackupAt
        } finally {
            snapshot.delete()
        }
    }

    /**
     * Copies the chosen file into the app and checks it is a healthy backup from this app.
     * Returns what it contains; call [scheduleRestore] to use it.
     */
    suspend fun inspect(uri: Uri): BackupInfo = withContext(Dispatchers.IO) {
        candidate.delete()
        val input = context.contentResolver.openInputStream(uri) ?: throw UserFacingException("Could not open that file")
        input.use { source -> candidate.outputStream().use { source.copyTo(it) } }
        try {
            validate(candidate)
        } catch (e: Exception) {
            candidate.delete()
            throw e as? UserFacingException ?: UserFacingException("That file is not a backup from this app")
        }
    }

    /**
     * Parks the inspected backup so it replaces the current data on the next start, after saving
     * a copy of the current data to [safetyCopy]. The app must restart afterwards.
     */
    suspend fun scheduleRestore() = withContext(Dispatchers.IO) {
        if (!candidate.exists()) throw UserFacingException("Choose a backup file first")
        safetyCopy.parentFile?.mkdirs()
        snapshotTo(safetyCopy)
        pending.delete()
        if (!candidate.renameTo(pending)) {
            candidate.copyTo(pending, overwrite = true)
            candidate.delete()
        }
    }

    /** Throws away an inspected file the owner decided not to restore. */
    fun discardCandidate() {
        candidate.delete()
    }

    /**
     * Swaps in a parked backup. Must run before Room opens the database (see AppContainer).
     * Returns true if a restore happened.
     */
    fun applyPendingRestore(): Boolean {
        val file = pending
        if (!file.exists()) return false
        val dbFile = context.getDatabasePath(databaseName)
        dbFile.parentFile?.mkdirs()
        // The write-ahead log belongs to the old database; leaving it would corrupt the new one.
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        if (!file.renameTo(dbFile)) {
            file.copyTo(dbFile, overwrite = true)
            file.delete()
        }
        prefs.edit().putLong(KEY_LAST_RESTORE, clock.millis()).commit()
        return true
    }

    /** A consistent copy of the live database, safe to take while it is in use. */
    private fun snapshotTo(target: File) {
        target.delete()
        val path = target.absolutePath.replace("'", "''")
        database().openHelper.writableDatabase.execSQL("VACUUM INTO '$path'")
    }

    private fun validate(file: File): BackupInfo {
        val header = file.inputStream().use { stream -> ByteArray(SQLITE_HEADER.size).also { stream.read(it) } }
        if (!header.contentEquals(SQLITE_HEADER)) throw UserFacingException("That file is not a backup from this app")

        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val version = db.version
            if (version > CoffeeShopDatabase.VERSION) {
                throw UserFacingException("This backup was made by a newer version of the app. Update the app first.")
            }
            val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            if (version < 1 || !tables.containsAll(REQUIRED_TABLES)) {
                throw UserFacingException("That file is not a backup from this app")
            }
            // Same version must mean the same tables; otherwise Room would refuse to open it.
            if (version == CoffeeShopDatabase.VERSION && identityHash(db) != currentIdentityHash()) {
                throw UserFacingException("This backup's layout does not match this version of the app")
            }
            val integrity = db.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else "" }
            if (integrity != "ok") throw UserFacingException("This backup file is damaged and cannot be restored")

            fun count(table: String) = db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> c.moveToFirst(); c.getInt(0) }
            val lastOrder = db.rawQuery("SELECT MAX(createdAt) FROM orders", null).use { c ->
                if (c.moveToFirst() && !c.isNull(0)) Instant.ofEpochMilli(c.getLong(0)) else null
            }
            return BackupInfo(version, count("products"), count("ingredients"), count("orders"), count("expenses"), lastOrder)
        }
    }

    private fun identityHash(db: SQLiteDatabase): String? =
        db.rawQuery("SELECT identity_hash FROM room_master_table LIMIT 1", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun currentIdentityHash(): String? =
        database().openHelper.readableDatabase.query("SELECT identity_hash FROM room_master_table LIMIT 1").use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private companion object {
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_FREQUENCY = "reminder_frequency"
        const val KEY_LAST_RESTORE = "last_restore_at"
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val REQUIRED_TABLES = setOf("room_master_table", "products", "ingredients", "orders", "expenses")
    }
}
