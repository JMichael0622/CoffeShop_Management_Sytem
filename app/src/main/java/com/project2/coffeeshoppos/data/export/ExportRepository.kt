package com.project2.coffeeshoppos.data.export

import android.content.Context
import android.net.Uri
import com.project2.coffeeshoppos.data.local.CoffeeShopDatabase
import com.project2.coffeeshoppos.data.repository.ReportRepository
import com.project2.coffeeshoppos.domain.DateRange
import com.project2.coffeeshoppos.domain.UserFacingException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Saves reports and raw data to a file the owner picked (Downloads, Google Drive...). Uses the
 * system "save as" picker, so the app needs no storage permission.
 */
class ExportRepository(
    private val context: Context,
    db: CoffeeShopDatabase,
    private val reports: ReportRepository,
    private val clock: Clock,
) {
    private val dao = db.exportDao()
    private val formats = ExportFormats(clock.zone)

    /** A file name such as "orders_2026-10-01_to_2026-10-03.csv". */
    fun suggestedFileName(type: ExportType, range: DateRange): String {
        val period = when {
            !type.needsRange -> LocalDate.now(clock).toString()
            range.isSingleDay -> range.start.toString()
            else -> "${range.start}_to_${range.endInclusive}"
        }
        return "${type.fileStem}_$period.${type.extension}"
    }

    /** Writes [type] for [range] to [uri]. Returns how many rows were written (0 for the PDF). */
    suspend fun export(type: ExportType, range: DateRange, uri: Uri): Int = withContext(Dispatchers.IO) {
        val from = range.start.atStartOfDay(clock.zone).toInstant()
        val to = range.endInclusive.plusDays(1).atStartOfDay(clock.zone).toInstant()
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw UserFacingException("Could not open the file to save to")
        output.use { stream ->
            if (type == ExportType.PROFIT_SUMMARY_PDF) {
                writeProfitSummaryPdf(reports.observeReport(range).first(), LocalDateTime.now(clock), stream)
                return@withContext 0
            }
            val csv = when (type) {
                ExportType.ORDERS_CSV -> formats.orders(dao.getOrders(from, to))
                ExportType.ITEMS_CSV -> formats.items(dao.getLines(from, to))
                ExportType.EXPENSES_CSV -> formats.expenses(dao.getExpenses(range.start, range.endInclusive))
                ExportType.PURCHASES_CSV -> formats.purchases(dao.getPurchases(from, to))
                ExportType.MOVEMENTS_CSV -> formats.movements(dao.getMovements(from, to))
                ExportType.STOCK_CSV -> formats.stock(dao.getIngredients())
                ExportType.PROFIT_SUMMARY_PDF -> error("Handled above")
            }
            stream.write(csv.build().toByteArray(Charsets.UTF_8))
            csv.rowCount
        }
    }
}
