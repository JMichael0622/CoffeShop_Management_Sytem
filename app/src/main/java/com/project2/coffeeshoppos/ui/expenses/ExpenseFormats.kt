package com.project2.coffeeshoppos.ui.expenses

import java.time.format.DateTimeFormatter
import java.util.Locale

val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
val MONTH_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
val DAY_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
val DAY_WITH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
