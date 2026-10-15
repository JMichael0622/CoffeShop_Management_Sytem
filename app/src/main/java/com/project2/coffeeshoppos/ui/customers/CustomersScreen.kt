package com.project2.coffeeshoppos.ui.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.dao.CustomerSummary
import com.project2.coffeeshoppos.domain.LoyaltyMath
import com.project2.coffeeshoppos.domain.LoyaltyRules
import com.project2.coffeeshoppos.domain.Permission
import com.project2.coffeeshoppos.ui.auth.LocalCurrentUser
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.EmptyState
import com.project2.coffeeshoppos.ui.components.FormDialog
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.StatusChip
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LAST_VISIT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

fun Instant.lastVisitLabel(): String = atZone(ZoneId.systemDefault()).format(LAST_VISIT)

/** Loyalty customers: search, see stamps, add new ones. */
@Composable
fun CustomersScreen(
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CustomersViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)

    SubScreenScaffold(
        title = "Customers",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            if (LocalCurrentUser.current.can(Permission.LOYALTY_SETTINGS)) {
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Loyalty card settings")
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Add customer") },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    if (state.rules.isEnabled) {
                        "Customers collect a stamp for each drink. ${state.rules.stampsForReward} stamps get a free drink."
                    } else {
                        "The loyalty card is turned off. An admin can turn it on in Manage › Loyalty card."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Search name or mobile number") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    if (state.customers.isEmpty() && !state.isLoading) {
                        EmptyState(
                            icon = R.drawable.ic_person,
                            title = if (state.query.isBlank()) "No customers yet" else "No match",
                            message = if (state.query.isBlank()) {
                                "Add customers here or from the POS cart when they order."
                            } else {
                                "No customer matches \"${state.query.trim()}\"."
                            }
                        )
                    } else {
                        Column {
                            state.customers.forEachIndexed { index, customer ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                CustomerRow(customer, state.rules, onClick = { onOpenCustomer(customer.customer.id) })
                            }
                        }
                    }
                }
            }
        }
    }

    state.dialog?.let { dialog ->
        CustomerFormDialog(
            title = "New customer",
            dialog = dialog,
            onNameChange = viewModel::onNameChange,
            onPhoneChange = viewModel::onPhoneChange,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::saveDialog,
            onDismiss = viewModel::closeDialog
        )
    }
}

@Composable
private fun CustomerRow(summary: CustomerSummary, rules: LoyaltyRules, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(summary.customer.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    summary.customer.phone?.let(LoyaltyMath::formatPhone),
                    "${summary.visits} " + if (summary.visits == 1) "visit" else "visits",
                    summary.lastVisitAt?.let { "last ${it.lastVisitLabel()}" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (rules.isEnabled) {
            val ready = LoyaltyMath.rewardsAvailable(summary.stamps, rules) > 0
            StatusChip(
                if (ready) "Free drink" else "${summary.stamps.coerceAtLeast(0) % rules.stampsForReward}/${rules.stampsForReward}",
                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Name, mobile number and note, used for adding and editing a customer. */
@Composable
fun CustomerFormDialog(
    title: String,
    dialog: CustomerDialogState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    FormDialog(
        title = title,
        confirmLabel = "Save",
        confirmEnabled = !dialog.isSaving && dialog.name.isNotBlank() && dialog.phone.isNotBlank(),
        onConfirm = onSave,
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = dialog.name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = dialog.phone,
            onValueChange = onPhoneChange,
            label = { Text("Mobile number") },
            placeholder = { Text("0917 123 4567") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = dialog.note,
            onValueChange = onNoteChange,
            label = { Text("Note (optional)") },
            placeholder = { Text("e.g. usual order: oat latte, less sugar") },
            singleLine = true,
            isError = dialog.error != null,
            supportingText = { Text(dialog.error ?: "Ask the customer first before saving their number.") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}
