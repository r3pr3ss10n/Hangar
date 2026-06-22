package eu.r3pr3ss10n.hangar.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.domain.TelegramStatus
import eu.r3pr3ss10n.hangar.ui.drive.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramScreen(
    onBack: () -> Unit,
    viewModel: TelegramViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmUnlink by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tg_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.tg_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.padding(top = 48.dp))
                return@Column
            }

            when (state.stage) {
                LinkStage.IDLE -> IdleContent(
                    status = state.status,
                    isPremium = state.isPremium,
                    busy = state.busy,
                    onLink = viewModel::beginLink,
                    onUnlink = { confirmUnlink = true },
                )

                LinkStage.PHONE -> WizardStep(
                    title = stringResource(R.string.tg_phone_title),
                    description = stringResource(R.string.tg_phone_desc),
                    value = state.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = stringResource(R.string.tg_phone_number),
                    keyboardType = KeyboardType.Phone,
                    busy = state.busy,
                    error = state.error,
                    confirmLabel = stringResource(R.string.tg_send_code),
                    onConfirm = viewModel::submitPhone,
                    onCancel = viewModel::cancelLink,
                )

                LinkStage.CODE -> WizardStep(
                    title = stringResource(R.string.tg_code_title),
                    description = stringResource(R.string.tg_code_desc),
                    value = state.code,
                    onValueChange = viewModel::onCodeChange,
                    label = stringResource(R.string.tg_login_code),
                    keyboardType = KeyboardType.Number,
                    busy = state.busy,
                    error = state.error,
                    confirmLabel = stringResource(R.string.tg_verify),
                    onConfirm = viewModel::submitCode,
                    onCancel = viewModel::cancelLink,
                )

                LinkStage.PASSWORD -> WizardStep(
                    title = stringResource(R.string.tg_password_title),
                    description = stringResource(R.string.tg_password_desc),
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = stringResource(R.string.tg_password_label),
                    password = true,
                    busy = state.busy,
                    error = state.error,
                    confirmLabel = stringResource(R.string.tg_finish),
                    onConfirm = viewModel::submitPassword,
                    onCancel = viewModel::cancelLink,
                )
            }
        }
    }

    if (confirmUnlink) {
        ConfirmDialog(
            title = stringResource(R.string.tg_unlink_title),
            message = stringResource(R.string.tg_unlink_message),
            confirmLabel = stringResource(R.string.tg_unlink),
            destructive = true,
            onConfirm = { confirmUnlink = false; viewModel.unlink() },
            onDismiss = { confirmUnlink = false },
        )
    }
}

@Composable
private fun IdleContent(
    status: TelegramStatus,
    isPremium: Boolean,
    busy: Boolean,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
) {
    when (status) {
        TelegramStatus.LINKED -> {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(stringResource(R.string.tg_account_linked), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text(
                if (isPremium) stringResource(R.string.tg_tier_premium) else stringResource(R.string.tg_tier_standard),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(
                onClick = onUnlink,
                enabled = !busy,
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
            ) { Text(stringResource(R.string.tg_unlink_account)) }
        }

        else -> {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(stringResource(R.string.tg_no_account_linked), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text(
                stringResource(R.string.tg_no_account_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Button(onClick = onLink, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tg_link_account))
            }
        }
    }
}

@Composable
private fun WizardStep(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    busy: Boolean,
    error: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(
        description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = onConfirm,
        enabled = !busy && value.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(confirmLabel)
        }
    }
    TextButton(onClick = onCancel, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.common_cancel)) }
}
