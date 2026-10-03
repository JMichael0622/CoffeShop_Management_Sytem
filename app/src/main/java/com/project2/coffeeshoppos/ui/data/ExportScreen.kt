package com.project2.coffeeshoppos.ui.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.data.export.ExportType
import com.project2.coffeeshoppos.domain.ReportPeriod
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.DatePickDialog
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

private enum class PickingDate { START, END }

@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var picking by rememberSaveable { mutableStateOf<PickingDate?>(null) }
    // Which export the "save as" picker is open for; survives rotation while the picker is showing.
    var pendingType by rememberSaveable { mutableStateOf<ExportType?>(null) }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)

    val onPicked: (Uri?) -> Unit = { uri ->
        pendingType?.let { viewModel.export(it, uri) }
        pendingType = null
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), onPicked)
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), onPicked)

    SubScreenScaffold(title = "Export reports", onBack = onBack, snackbarHostState = snackbarHostState) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Save reports and records as files you can open in Excel or Google Sheets, print, or send to " +
                        "your accountant. You choose where each file is saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { SectionHeader("Period") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ReportPeriod.entries) { period ->
                        FilterChip(selected = state.period == period, onClick = { viewModel.onPeriod(period) }, label = { Text(period.label) })
                    }
                }
            }
            item {
                if (state.period == ReportPeriod.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picking = PickingDate.START }, modifier = Modifier.weight(1f)) {
                            Text("From ${state.range.start.format(DAY)}")
                        }
                        OutlinedButton(onClick = { picking = PickingDate.END }, modifier = Modifier.weight(1f)) {
                            Text("To ${state.range.endInclusive.format(DAY)}")
                        }
                    }
                } else {
                    val range = state.range
                    Text(
                        if (range.isSingleDay) range.start.format(DAY) else "${range.start.format(DAY)} – ${range.endInclusive.format(DAY)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            item {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    Column {
                        ExportType.entries.forEachIndexed { index, type ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${type.label} (${type.extension.uppercase()})", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        type.description + if (type.needsRange) "" else " · ignores the period",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (state.busy == type) {
                                    CircularProgressIndicator(Modifier.padding(horizontal = 20.dp).size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            pendingType = type
                                            val name = viewModel.suggestedFileName(type)
                                            if (type == ExportType.PROFIT_SUMMARY_PDF) savePdf.launch(name) else saveCsv.launch(name)
                                        },
                                        enabled = state.busy == null
                                    ) { Text("Save") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Amounts are in pesos without the ₱ sign, so spreadsheets can add them up. Times are shown in this phone's time zone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    when (picking) {
        PickingDate.START -> DatePickDialog(
            initial = state.range.start, latest = state.today, onPick = viewModel::onCustomStart, onDismiss = { picking = null }
        )
        PickingDate.END -> DatePickDialog(
            initial = state.range.endInclusive, latest = state.today, onPick = viewModel::onCustomEnd, onDismiss = { picking = null }
        )
        null -> Unit
    }
}
