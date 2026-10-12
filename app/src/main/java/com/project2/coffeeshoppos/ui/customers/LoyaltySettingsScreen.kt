package com.project2.coffeeshoppos.ui.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.domain.LoyaltyRules
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.SectionHeader
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.util.formatPeso
import com.project2.coffeeshoppos.util.parsePesoToCentavos

/** The owner's loyalty card rules: on/off, stamps per free drink, the free drink limit, and which categories count. */
@Composable
fun LoyaltySettingsScreen(
    onBack: () -> Unit,
    viewModel: LoyaltySettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)

    SubScreenScaffold(title = "Loyalty card", onBack = onBack, snackbarHostState = snackbarHostState) { innerPadding ->
        val form = state.form
        if (state.isLoading || form == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@SubScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable { viewModel.onEnabledChange(!form.isEnabled) }.heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Loyalty card", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "When off, customers can still be added but earn no stamps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = form.isEnabled, onCheckedChange = viewModel::onEnabledChange)
                        }
                        OutlinedTextField(
                            value = form.stampsText,
                            onValueChange = viewModel::onStampsChange,
                            label = { Text("Stamps for a free drink") },
                            supportingText = {
                                Text("${LoyaltyRules.STAMPS_RANGE.first} to ${LoyaltyRules.STAMPS_RANGE.last}. One stamp per drink.")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = form.capText,
                            onValueChange = viewModel::onCapChange,
                            label = { Text("Free drink worth up to") },
                            prefix = { Text("₱") },
                            singleLine = true,
                            isError = form.error != null,
                            supportingText = {
                                Text(
                                    form.error ?: (
                                        "A pricier drink gets this much off; the customer pays the rest. " +
                                            "Add-ons are always charged."
                                        )
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        val stamps = form.stampsText.toIntOrNull()
                        val cap = parsePesoToCentavos(form.capText)
                        if (form.isEnabled && stamps != null && cap != null) {
                            Text(
                                "Customers get a free drink worth up to ${formatPeso(cap)} after every $stamps drinks.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = viewModel::save,
                            enabled = state.hasChanges && !form.isSaving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text(if (form.isSaving) "Saving…" else "Save") }
                    }
                }
            }
            item { SectionHeader("Categories that earn stamps") }
            item {
                Text(
                    "Items in these categories earn a stamp and can be the free drink. Turn off categories like pastries.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    if (state.categories.isEmpty()) {
                        EmptyState(
                            icon = R.drawable.ic_category,
                            title = "No categories yet",
                            message = "Every product earns stamps until you sort them into categories."
                        )
                    } else {
                        Column {
                            state.categories.forEachIndexed { index, category ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setCategoryEarnsStamps(category, !category.earnsStamps) }
                                        .heightIn(min = 56.dp)
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = category.earnsStamps,
                                        onCheckedChange = { viewModel.setCategoryEarnsStamps(category, it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Products with no category always earn stamps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
