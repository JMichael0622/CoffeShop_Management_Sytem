package com.project2.coffeeshoppos.ui.inventory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.entity.IngredientEntity
import com.project2.coffeeshoppos.domain.MeasureUnit
import com.project2.coffeeshoppos.domain.StockLevel
import com.project2.coffeeshoppos.domain.stockLevel
import com.project2.coffeeshoppos.ui.components.StatusChip
import com.project2.coffeeshoppos.ui.theme.LocalCoffeeExtraColors

/**
 * A quantity box with a unit picker beside it, e.g. "2.5" [kg ▾]. The picker only offers
 * units that convert to the ingredient's base unit, and is hidden when there is only one (pc).
 */
@Composable
fun QuantityField(
    value: String,
    onValueChange: (String) -> Unit,
    unit: MeasureUnit,
    onUnitChange: (MeasureUnit) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    val units = unit.compatibleUnits
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            suffix = if (units.size == 1) {
                { Text(unit.symbol) }
            } else null,
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
            modifier = Modifier.weight(1f)
        )
        if (units.size > 1) {
            var expanded by remember { mutableStateOf(false) }
            Box(Modifier.padding(start = 4.dp)) {
                TextButton(onClick = { expanded = true }) {
                    Text(unit.symbol, style = MaterialTheme.typography.titleMedium)
                    Icon(
                        painterResource(R.drawable.ic_arrow_down),
                        contentDescription = "Change unit",
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    units.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { onUnitChange(option); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

/** "Out of stock" / "Low stock" / "Archived" label for an ingredient, or nothing when all is well. */
@Composable
fun IngredientStatusChip(ingredient: IngredientEntity, modifier: Modifier = Modifier) {
    when {
        ingredient.isArchived -> StatusChip("Archived", modifier)
        ingredient.stockLevel == StockLevel.OUT -> StatusChip("Out of stock", modifier, MaterialTheme.colorScheme.error)
        ingredient.stockLevel == StockLevel.LOW -> StatusChip("Low stock", modifier, LocalCoffeeExtraColors.current.warning)
    }
}
