package com.project2.coffeeshoppos.ui.accounts

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.data.local.entity.UserEntity
import com.project2.coffeeshoppos.domain.UserRole
import com.project2.coffeeshoppos.ui.AppViewModelProvider
import com.project2.coffeeshoppos.ui.auth.PinField
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.FormDialog
import com.project2.coffeeshoppos.ui.components.MessageEffect
import com.project2.coffeeshoppos.ui.components.StatusChip
import com.project2.coffeeshoppos.ui.components.SubScreenScaffold
import com.project2.coffeeshoppos.ui.theme.LocalCoffeeExtraColors

/** Admin only: who can sign in, as admin or staff, and their PINs. */
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbarHostState, viewModel::onMessageShown)

    SubScreenScaffold(
        title = "Accounts",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Add account") },
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
                    "Admins can do everything. Staff can take orders, manage inventory and customers, export reports " +
                        "and save backups, but can't refund, change the menu, prices or expenses, view reports, " +
                        "restore backups or manage accounts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                CoffeeCard(Modifier.fillMaxWidth()) {
                    Column {
                        state.accounts.forEachIndexed { index, account ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            AccountRow(account, isMe = account.id == state.currentUserId, onClick = { viewModel.openAccount(account) })
                        }
                    }
                }
            }
        }
    }

    state.dialog?.let { dialog -> AccountDialog(dialog, isMe = dialog.account?.id == state.currentUserId, viewModel) }
}

@Composable
private fun AccountRow(account: UserEntity, isMe: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            account.name + if (isMe) " (you)" else "",
            style = MaterialTheme.typography.bodyLarge,
            color = if (account.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (!account.isActive) StatusChip("Deactivated", color = LocalCoffeeExtraColors.current.warning)
        StatusChip(account.role.label, color = if (account.role == UserRole.ADMIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountDialog(dialog: AccountDialogState, isMe: Boolean, viewModel: AccountsViewModel) {
    val existing = dialog.account
    FormDialog(
        title = existing?.name ?: "New account",
        confirmLabel = "Save",
        confirmEnabled = !dialog.isSaving && dialog.name.isNotBlank() &&
            (existing != null || dialog.pin.length >= 4),
        onConfirm = viewModel::save,
        onDismiss = viewModel::closeDialog
    ) {
        if (existing == null) {
            OutlinedTextField(
                value = dialog.name,
                onValueChange = viewModel::onName,
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text("Role", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserRole.entries.forEach { role ->
                FilterChip(
                    selected = dialog.role == role,
                    onClick = { viewModel.onRole(role) },
                    enabled = !isMe,
                    label = { Text(role.label) }
                )
            }
        }
        if (isMe) {
            Text("You can't change your own role.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PinField(
            dialog.pin, viewModel::onPin,
            if (existing == null) "PIN (4 to 6 digits)" else "New PIN (leave blank to keep)",
            ImeAction.Next,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        if (existing == null || dialog.pin.isNotEmpty()) {
            PinField(dialog.confirmPin, viewModel::onConfirmPin, "Type the PIN again", ImeAction.Done, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        dialog.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        if (existing != null && !isMe) {
            TextButton(onClick = { viewModel.setActive(!existing.isActive) }, enabled = !dialog.isSaving) {
                Text(
                    if (existing.isActive) "Deactivate account" else "Reactivate account",
                    color = if (existing.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
