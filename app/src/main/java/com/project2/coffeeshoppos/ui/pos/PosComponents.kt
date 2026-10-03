package com.project2.coffeeshoppos.ui.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.domain.Discount
import com.project2.coffeeshoppos.util.centavosToInput
import com.project2.coffeeshoppos.util.parsePesoToCentavos

/** − 2 + buttons. Going below [min] is not possible; the minus button is disabled there. */
@Composable
fun QuantityStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 99,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilledTonalIconButton(
            onClick = { onValueChange(value - 1) },
            enabled = value > min,
            modifier = Modifier.semantics { contentDescription = "Fewer $label" }
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp)
        )
        FilledTonalIconButton(onClick = { onValueChange(value + 1) }, enabled = value < max) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = "More $label")
        }
    }
}

private val PERCENT_PRESETS = listOf(5, 10, 20)
private val REASON_SUGGESTIONS = listOf("Senior citizen", "PWD", "Promo", "Staff", "Loyalty")

/** Picks a percentage or fixed-amount discount for the whole order, with a reason. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscountDialog(
    current: Discount,
    currentReason: String,
    onApply: (Discount, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var percent by remember { mutableStateOf((current as? Discount.Percent)?.percent) }
    var amountText by remember {
        mutableStateOf((current as? Discount.Amount)?.let { centavosToInput(it.centavos) } ?: "")
    }
    var reason by remember { mutableStateOf(currentReason) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discount") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PERCENT_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = percent == preset,
                            onClick = { percent = preset; amountText = ""; error = null },
                            label = { Text("$preset%") }
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; percent = null; error = null },
                    label = { Text("Or a fixed amount") },
                    prefix = { Text("₱") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; error = null },
                    label = { Text("Reason") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REASON_SUGGESTIONS.forEach { suggestion ->
                        SuggestionChip(onClick = { reason = suggestion; error = null }, label = { Text(suggestion) })
                    }
                }
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val discount = when {
                    percent != null -> Discount.Percent(percent!!)
                    amountText.isNotBlank() -> parsePesoToCentavos(amountText)?.let { Discount.Amount(it) }
                    else -> Discount.None
                }
                error = when {
                    discount == null -> "Enter a valid amount, e.g. 20 or 25.50"
                    discount != Discount.None && reason.isBlank() -> "Give a reason, e.g. Senior citizen"
                    else -> null
                }
                if (error == null && discount != null) onApply(discount, reason)
            }) { Text("Apply") }
        },
        dismissButton = {
            Row {
                if (current != Discount.None) {
                    TextButton(onClick = { onApply(Discount.None, "") }) { Text("Remove") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
