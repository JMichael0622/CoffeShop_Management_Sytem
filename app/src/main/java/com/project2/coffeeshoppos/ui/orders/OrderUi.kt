package com.project2.coffeeshoppos.ui.orders

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.project2.coffeeshoppos.domain.OrderStatus
import com.project2.coffeeshoppos.ui.components.StatusChip
import com.project2.coffeeshoppos.ui.theme.LocalCoffeeExtraColors
import java.time.format.DateTimeFormatter
import java.util.Locale

val ORDER_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
val ORDER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH)
val ORDER_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.ENGLISH)

@Composable
fun OrderStatusChip(status: OrderStatus, modifier: Modifier = Modifier) {
    val color: Color = when (status) {
        OrderStatus.PENDING -> LocalCoffeeExtraColors.current.warning
        OrderStatus.PROCESSING -> MaterialTheme.colorScheme.secondary
        OrderStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        OrderStatus.CANCELLED, OrderStatus.REFUNDED -> MaterialTheme.colorScheme.error
    }
    StatusChip(status.label, modifier, color)
}
