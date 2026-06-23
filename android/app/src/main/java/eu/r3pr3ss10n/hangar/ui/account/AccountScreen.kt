package eu.r3pr3ss10n.hangar.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.domain.User
import eu.r3pr3ss10n.hangar.ui.drive.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    user: User,
    onOpenMyLinks: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenTelegram: () -> Unit,
    onOpenAbout: () -> Unit,
    onLoggedOut: () -> Unit,
    onForgetServer: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.account_title)) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(user.username, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(stringResource(if (user.isAdmin) R.string.account_role_admin else R.string.role_user)) },
            )
            HorizontalDivider()

            ListItem(
                leadingContent = { Icon(Icons.Filled.Storage, null) },
                headlineContent = { Text(stringResource(R.string.account_storage_used)) },
                trailingContent = { Text(state.storageText) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Cloud, null) },
                headlineContent = { Text(stringResource(R.string.account_generate_thumbnails)) },
                supportingContent = { Text(stringResource(R.string.account_generate_thumbnails_desc)) },
                trailingContent = {
                    Switch(
                        checked = state.generateThumbnails,
                        onCheckedChange = viewModel::setGenerateThumbnails,
                    )
                },
            )

            if (user.isAdmin) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.account_admin_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenUsers),
                    leadingContent = { Icon(Icons.Filled.People, null) },
                    headlineContent = { Text(stringResource(R.string.nav_users)) },
                )
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenTelegram),
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                    headlineContent = { Text(stringResource(R.string.account_telegram_account)) },
                )
            }

            HorizontalDivider()
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenMyLinks),
                leadingContent = { Icon(Icons.Filled.Link, null) },
                headlineContent = { Text(stringResource(R.string.nav_my_links)) },
            )
            ListItem(
                modifier = Modifier.clickable(onClick = onOpenAbout),
                leadingContent = { Icon(Icons.Filled.Info, null) },
                headlineContent = { Text(stringResource(R.string.about_title)) },
            )

            HorizontalDivider()
            ListItem(
                modifier = Modifier.clickable { confirmLogout = true },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                headlineContent = { Text(stringResource(R.string.account_sign_out)) },
            )
            ListItem(
                modifier = Modifier.clickable { confirmForget = true },
                headlineContent = {
                    Text(stringResource(R.string.account_disconnect_server), color = MaterialTheme.colorScheme.error)
                },
            )
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = stringResource(R.string.account_sign_out_confirm_title),
            message = stringResource(R.string.account_sign_out_confirm_message),
            confirmLabel = stringResource(R.string.account_sign_out),
            onConfirm = { confirmLogout = false; viewModel.logout(onLoggedOut) },
            onDismiss = { confirmLogout = false },
        )
    }
    if (confirmForget) {
        ConfirmDialog(
            title = stringResource(R.string.account_disconnect_server_confirm_title),
            message = stringResource(R.string.account_disconnect_server_confirm_message),
            confirmLabel = stringResource(R.string.account_disconnect),
            destructive = true,
            onConfirm = { confirmForget = false; onForgetServer() },
            onDismiss = { confirmForget = false },
        )
    }
}
