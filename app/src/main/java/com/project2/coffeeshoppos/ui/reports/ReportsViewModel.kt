package com.project2.coffeeshoppos.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project2.coffeeshoppos.data.repository.ReportRepository
import com.project2.coffeeshoppos.data.repository.SalesReport
import com.project2.coffeeshoppos.domain.DateRange
import com.project2.coffeeshoppos.domain.ReportPeriod
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ReportsUiState(
    val period: ReportPeriod = ReportPeriod.TODAY,
    val range: DateRange = DateRange.day(LocalDate.MIN),
    val today: LocalDate = LocalDate.MIN,
    val report: SalesReport? = null,
)

private data class Selection(val period: ReportPeriod, val custom: DateRange)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(private val repository: ReportRepository) : ViewModel() {
    private val selection = MutableStateFlow(
        Selection(ReportPeriod.TODAY, DateRange.day(repository.today()))
    )

    private fun Selection.range(): DateRange =
        if (period == ReportPeriod.CUSTOM) custom else period.range(repository.today())

    val uiState: StateFlow<ReportsUiState> = selection
        .map { it.range() }
        .distinctUntilChanged()
        .flatMapLatest { repository.observeReport(it) }
        .let { reports ->
            combine(selection, reports) { selection, report ->
                ReportsUiState(
                    period = selection.period,
                    range = selection.range(),
                    today = repository.today(),
                    // While a new range loads, never show the old range's numbers under the new title.
                    report = report.takeIf { it.range == selection.range() }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun onPeriod(period: ReportPeriod) = selection.update {
        // Switching to Custom starts from whatever range was showing.
        if (period == ReportPeriod.CUSTOM && it.period != ReportPeriod.CUSTOM) it.copy(period = period, custom = it.range())
        else it.copy(period = period)
    }

    /** Moves the start of the custom range, keeping it no later than the end. */
    fun onCustomStart(date: LocalDate) = selection.update {
        it.copy(custom = DateRange(date, maxOf(date, it.custom.endInclusive)))
    }

    /** Moves the end of the custom range, keeping it no earlier than the start. */
    fun onCustomEnd(date: LocalDate) = selection.update {
        it.copy(custom = DateRange(minOf(date, it.custom.start), date))
    }
}
