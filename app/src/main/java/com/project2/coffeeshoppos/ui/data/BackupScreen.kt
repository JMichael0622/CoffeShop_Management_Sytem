package com.project2.coffeeshoppos.ui.data

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.backup.BackupInfo
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.InfoBanner
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.ENGLISH)
private fun Instant.local(): String = atZone(ZoneId.systemDefault()).format(WHEN)

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        viewModel.backupTo(it)
    }
    // Some file managers do not recognise .db files, so any file may be chosen; the app checks it.
    val chooseBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.inspect(it) }

    LaunchedEffect(state.restartToRestore) {
        if (state.restartToRestore) restartApp(context)
    }

    SubScreenScaffold(title = "Backup & restore", onBack = onBack, snackbarHostState = snackbarHostState) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "All shop data lives only on this phone. If the phone is lost, broken or reset, it is gone unless you " +
                    "have a backup. Save one regularly and keep it somewhere else, such as Google Drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.lastRestoreAt?.let {
                InfoBanner(icon = R.drawable.ic_info, text = "Data was restored from a backup on ${it.local()}.")
            }

            CoffeeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Save a backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.lastBackupAt?.let { "Last backup: ${it.local()}" } ?: "No backup saved from this phone yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Includes the menu, recipes, stock, orders, expenses and the audit log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { saveBackup.launch(viewModel.suggestedFileName()) },
                        enabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text(if (state.isWorking) "Working…" else "Save backup") }
                }
            }

            CoffeeCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restore from a backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Replaces everything in the app with the backup's data. You will see what the file contains " +
                            "before anything changes, and a copy of the current data is kept on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { chooseBackup.launch(arrayOf("*/*")) },
                        enabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Choose backup file") }
                }
            }
        }
    }

    state.candidate?.let { info ->
        ConfirmRestoreDialog(info, onConfirm = viewModel::confirmRestore, onDismiss = viewModel::cancelRestore)
    }
}

@Composable
private fun ConfirmRestoreDialog(info: BackupInfo, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore this backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("The backup contains:")
                Text("• ${info.products} products, ${info.ingredients} ingredients")
                Text("• ${info.orders} orders" + (info.lastOrderAt?.let { ", the latest on ${it.local()}" } ?: ""))
                Text("• ${info.expenses} expenses")
                Text(
                    "Everything currently in the app will be replaced and the app will restart. " +
                        "Anything recorded after this backup was made will be lost.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Restore and restart", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Relaunches the app in a fresh process, so the staged restore is applied before the database opens. */
private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
