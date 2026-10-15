package com.project2.coffeeshoppos.ui.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.backup.BackupInfo
import com.project2.coffeeshoppos.data.backup.BackupManager
import com.project2.coffeeshoppos.domain.BackupFrequency
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val lastBackupAt: Instant? = null,
    val lastRestoreAt: Instant? = null,
    val frequency: BackupFrequency = BackupFrequency.WEEKLY,
    val isWorking: Boolean = false,
    /** A checked backup waiting for the owner to confirm the restore. */
    val candidate: BackupInfo? = null,
    /** Set once a restore is staged; the screen then restarts the app to apply it. */
    val restartToRestore: Boolean = false,
    val message: String? = null,
)

class BackupViewModel(private val backups: BackupManager) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState(backups.lastBackupAt, backups.lastRestoreAt, backups.frequency.value))
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun suggestedFileName(): String = backups.suggestedFileName()
    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onFrequency(value: BackupFrequency) {
        backups.setFrequency(value)
        _uiState.update { it.copy(frequency = value) }
    }

    /** Called with the file chosen in the "save as" picker; null means they backed out. */
    fun backupTo(uri: Uri?) {
        if (uri == null) return
        run(success = "Backup saved. Keep it somewhere safe, like Google Drive.") {
            backups.backupTo(uri)
            _uiState.update { it.copy(lastBackupAt = backups.lastBackupAt) }
        }
    }

    /** Checks the chosen file and, if it is a good backup, asks the owner to confirm. */
    fun inspect(uri: Uri?) {
        if (uri == null) return
        run(success = null) {
            val info = backups.inspect(uri)
            _uiState.update { it.copy(candidate = info) }
        }
    }

    fun cancelRestore() {
        backups.discardCandidate()
        _uiState.update { it.copy(candidate = null) }
    }

    fun confirmRestore() {
        run(success = null) {
            backups.scheduleRestore()
            _uiState.update { it.copy(candidate = null, restartToRestore = true) }
        }
    }

    private fun run(success: String?, block: suspend () -> Unit) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val message = try {
                block()
                success
            } catch (e: Exception) {
                e.message ?: "Something went wrong"
            }
            _uiState.update { it.copy(isWorking = false, message = message) }
        }
    }
}
