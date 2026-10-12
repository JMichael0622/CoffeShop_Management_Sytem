package com.project2.coffeeshoppos.ui.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.dao.CustomerSummary
import com.project2.coffeeshoppos.domain.LoyaltyMath
import com.project2.coffeeshoppos.domain.LoyaltyRules
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.IconBadge
import com.project2.coffeeshoppos.ui.components.StatusChip
import com.project2.coffeeshoppos.util.formatPeso

/** The cart's loyalty card: attach a customer, see their stamps, use a free drink. */
@Composable
fun LoyaltyCartCard(state: PosUiState, viewModel: PosViewModel) {
    var choosingRewardLine by remember { mutableStateOf(false) }
    val customer = state.customer
    CoffeeCard(Modifier.fillMaxWidth()) {
        if (customer == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::openCustomerPicker)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconBadge(icon = R.drawable.ic_person)
                Column(Modifier.weight(1f)) {
                    Text("Loyalty customer", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Add a customer to give them stamps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Add", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            return@CoffeeCard
        }

        val rules = state.rules
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(icon = R.drawable.ic_person)
                Column(Modifier.weight(1f)) {
                    Text(customer.customer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    customer.customer.phone?.let {
                        Text(LoyaltyMath.formatPhone(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = viewModel::removeCustomer) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove customer from this order")
                }
            }
            StampProgress(customer.stamps, rules)
            if (state.stampsToEarn > 0) {
                Text(
                    "+${state.stampsToEarn} " + (if (state.stampsToEarn == 1) "stamp" else "stamps") + " with this order",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val rewardLine = state.rewardLine
            when {
                rewardLine != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Free: ${rewardLine.title}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "−${formatPeso(state.rewardCentavos)}" +
                                if (rewardLine.unitPriceCentavos > rules.rewardCapCentavos) " (limit ${formatPeso(rules.rewardCapCentavos)})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.rewardableLines.size > 1) TextButton(onClick = { choosingRewardLine = true }) { Text("Change") }
                    TextButton(onClick = viewModel::removeReward) { Text("Remove") }
                }
                state.canUseReward -> OutlinedButton(onClick = { viewModel.useReward() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_star), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Use free drink", modifier = Modifier.padding(start = 8.dp))
                }
                rules.isEnabled && customer.stamps >= rules.stampsForReward -> Text(
                    "Free drink ready — add a drink that earns stamps to use it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (choosingRewardLine) {
        RewardLineDialog(
            state = state,
            onChoose = { viewModel.useReward(it); choosingRewardLine = false },
            onDismiss = { choosingRewardLine = false }
        )
    }
}

/** "7 of 10 stamps" with a bar, or "Free drink ready". */
@Composable
fun StampProgress(stamps: Int, rules: LoyaltyRules, modifier: Modifier = Modifier) {
    if (!rules.isEnabled) {
        Text("Loyalty card is turned off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val rewards = LoyaltyMath.rewardsAvailable(stamps, rules)
    val onCard = stamps.coerceAtLeast(0) % rules.stampsForReward
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$onCard of ${rules.stampsForReward} stamps", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (rewards > 0) {
                StatusChip(
                    if (rewards == 1) "Free drink ready" else "$rewards free drinks ready",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        LinearProgressIndicator(progress = { onCard.toFloat() / rules.stampsForReward }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RewardLineDialog(state: PosUiState, onChoose: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which drink is free?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                state.rewardableLines.forEach { line ->
                    val selected = state.rewardLine?.line?.key == line.line.key
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onChoose(line.line.key) })
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(line.title, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Text(formatPeso(LoyaltyMath.rewardAmount(line.unitPriceCentavos, state.rules)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/** Find a customer by name or number, or add a new one. */
@Composable
fun CustomerPickerDialog(state: PosUiState, viewModel: PosViewModel) {
    val picker = state.picker ?: return
    if (picker.isAdding) {
        AlertDialog(
            onDismissRequest = viewModel::closeCustomerPicker,
            title = { Text("New customer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = picker.name,
                        onValueChange = viewModel::onNewCustomerName,
                        label = { Text("Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = picker.phone,
                        onValueChange = viewModel::onNewCustomerPhone,
                        label = { Text("Mobile number") },
                        placeholder = { Text("0917 123 4567") },
                        singleLine = true,
                        isError = picker.error != null,
                        supportingText = { Text(picker.error ?: "Ask the customer first before saving their number.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::saveNewCustomer,
                    enabled = !picker.isSaving && picker.name.isNotBlank() && picker.phone.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelAddCustomer) { Text("Back") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = viewModel::closeCustomerPicker,
        title = { Text("Loyalty customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = picker.query,
                    onValueChange = viewModel::onCustomerQuery,
                    label = { Text("Name or mobile number") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (state.customerResults.isEmpty()) {
                        Text(
                            if (picker.query.isBlank()) "No customers yet." else "No match for \"${picker.query.trim()}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    state.customerResults.forEachIndexed { index, result ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CustomerResultRow(result, state.rules, onClick = { viewModel.selectCustomer(result.customer.id) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::startAddCustomer) { Text("New customer") } },
        dismissButton = { TextButton(onClick = viewModel::closeCustomerPicker) { Text("Cancel") } }
    )
}

@Composable
private fun CustomerResultRow(result: CustomerSummary, rules: LoyaltyRules, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(result.customer.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            result.customer.phone?.let {
                Text(LoyaltyMath.formatPhone(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (rules.isEnabled) {
            val ready = LoyaltyMath.rewardsAvailable(result.stamps, rules) > 0
            StatusChip(
                if (ready) "Free drink" else "${result.stamps % rules.stampsForReward}/${rules.stampsForReward}",
                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
