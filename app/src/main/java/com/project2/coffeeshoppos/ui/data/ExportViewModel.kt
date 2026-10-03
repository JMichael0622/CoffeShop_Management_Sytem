package com.project2.coffeeshoppos.ui.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.export.ExportRepository
import com.project2.coffeeshoppos.data.export.ExportType
import com.project2.coffeeshoppos.data.repository.ReportRepository
import com.project2.coffeeshoppos.domain.DateRange
import com.project2.coffeeshoppos.domain.ReportPeriod
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExportUiState(
    val period: ReportPeriod = ReportPeriod.THIS_MONTH,
    val customRange: DateRange,
    val today: LocalDate,
    /** The export being written right now, if any. */
    val busy: ExportType? = null,
    val message: String? = null,
) {
    val range: DateRange get() = if (period == ReportPeriod.CUSTOM) customRange else period.range(today)
}

class ExportViewModel(
    private val exports: ExportRepository,
    private val reports: ReportRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        reports.today().let { ExportUiState(customRange = DateRange.day(it), today = it) }
    )
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun onPeriod(period: ReportPeriod) = _uiState.update {
        val today = reports.today()
        if (period == ReportPeriod.CUSTOM && it.period != ReportPeriod.CUSTOM) it.copy(period = period, customRange = it.range, today = today)
        else it.copy(period = period, today = today)
    }

    fun onCustomStart(date: LocalDate) = _uiState.update {
        it.copy(customRange = DateRange(date, maxOf(date, it.customRange.endInclusive)))
    }

    fun onCustomEnd(date: LocalDate) = _uiState.update {
        it.copy(customRange = DateRange(minOf(date, it.customRange.start), date))
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun suggestedFileName(type: ExportType): String = exports.suggestedFileName(type, _uiState.value.range)

    /** Called with the file the owner picked; null means they backed out of the picker. */
    fun export(type: ExportType, uri: Uri?) {
        if (uri == null) return
        val range = _uiState.value.range
        _uiState.update { it.copy(busy = type) }
        viewModelScope.launch {
            val message = try {
                val rows = exports.export(type, range, uri)
                if (type == ExportType.PROFIT_SUMMARY_PDF) "Profit summary saved"
                else "${type.label} saved · $rows " + if (rows == 1) "row" else "rows"
            } catch (e: Exception) {
                "Could not save: ${e.message}"
            }
            _uiState.update { it.copy(busy = null, message = message) }
        }
    }
}
