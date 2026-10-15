package com.project2.coffeeshoppos.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.project2.coffeeshoppos.R
import com.project2.coffeeshoppos.ui.components.CoffeeCard
import com.project2.coffeeshoppos.ui.components.InfoBanner
import com.project2.coffeeshoppos.ui.components.StatusChip

/** First launch (or after restoring a backup with no accounts): create the admin account. */
@Composable
fun SetupAdminScreen(state: AuthUiState, viewModel: AuthViewModel) {
    val form = state.form
    AuthFrame(title = "Welcome to My Coffee Shop", subtitle = "Create the admin account. The admin can do everything, including adding staff accounts.") {
        OutlinedTextField(
            value = form.name,
            onValueChange = viewModel::onName,
            label = { Text("Your name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        PinField(form.pin, viewModel::onPin, "PIN (4 to 6 digits)", ImeAction.Next)
        PinField(form.confirmPin, viewModel::onConfirmPin, "Type the PIN again", ImeAction.Done, onDone = viewModel::createAdmin)
        InfoBanner(
            icon = R.drawable.ic_warning,
            text = "Remember this PIN. It can't be recovered if forgotten, so later add a second admin as a spare."
        )
        form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = viewModel::createAdmin,
            enabled = !form.isWorking && form.name.isNotBlank() && form.pin.length >= 4 && form.confirmPin.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(if (form.isWorking) "Creating…" else "Create admin account") }
    }
}

/** Choose who you are and enter your PIN. */
@Composable
fun SignInScreen(state: AuthUiState, viewModel: AuthViewModel) {
    val form = state.form
    AuthFrame(title = "Who's working?", subtitle = "Choose your name and enter your PIN.") {
        CoffeeCard(Modifier.fillMaxWidth()) {
            Column {
                state.accounts.forEachIndexed { index, account ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = form.selectedId == account.id,
                                role = Role.RadioButton,
                                onClick = { viewModel.onSelect(account.id) }
                            )
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(selected = form.selectedId == account.id, onClick = null)
                        Text(account.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        StatusChip(account.role.label)
                    }
                }
            }
        }
        PinField(form.pin, viewModel::onPin, "PIN", ImeAction.Done, onDone = viewModel::signIn)
        form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = viewModel::signIn,
            enabled = !form.isWorking && form.selectedId != null && form.pin.length >= 4,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(if (form.isWorking) "Signing in…" else "Sign in") }
        Text(
            "Forgot your PIN? Ask an admin to set a new one in Manage › Accounts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AuthFrame(title: String, subtitle: String, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp).align(Alignment.CenterHorizontally)
            )
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
    )
}
