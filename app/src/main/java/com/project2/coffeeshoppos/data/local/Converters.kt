package com.project2.coffeeshoppos.data.local

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Teaches Room how to store types SQLite does not know about.
 * Enums need no converter: Room stores them by name automatically.
 */
class Converters {
    /** Moments in time are stored as milliseconds since 1970 (UTC). */
    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /** Calendar dates (no time) are stored as days since 1970-01-01. */
    @TypeConverter
    fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    /** Exact decimals are stored as text, e.g. "12.500000", so no precision is lost. */
    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? = value?.let(::BigDecimal)
}
