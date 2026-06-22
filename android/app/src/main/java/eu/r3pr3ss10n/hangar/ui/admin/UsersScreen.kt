package eu.r3pr3ss10n.hangar.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.domain.Role
import eu.r3pr3ss10n.hangar.domain.User
import eu.r3pr3ss10n.hangar.ui.drive.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    currentUserId: String,
    onBack: () -> Unit,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<User?>(null) }
    var passwordTarget by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.users_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.users_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Filled.Add, stringResource(R.string.users_add_user)) }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.users, key = { it.id }) { u ->
                        UserRow(
                            user = u,
                            isSelf = u.id == currentUserId,
                            onSetPassword = { passwordTarget = u },
                            onDelete = { deleteTarget = u },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateUserDialog(
            onConfirm = { name, pass, role -> viewModel.create(name, pass, role); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }

    deleteTarget?.let { u ->
        ConfirmDialog(
            title = stringResource(R.string.users_delete_title),
            message = stringResource(R.string.users_delete_desc, u.username),
            confirmLabel = stringResource(R.string.common_delete),
            destructive = true,
            onConfirm = { viewModel.delete(u.id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }

    passwordTarget?.let { u ->
        SetPasswordDialog(
            username = u.username,
            onConfirm = { pass -> viewModel.setPassword(u.id, pass); passwordTarget = null },
            onDismiss = { passwordTarget = null },
        )
    }
}

@Composable
private fun UserRow(
    user: User,
    isSelf: Boolean,
    onSetPassword: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(user.username + if (isSelf) " " + stringResource(R.string.users_you) else "") },
        supportingContent = { Text(if (user.isAdmin) stringResource(R.string.role_admin) else stringResource(R.string.role_user)) },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.users_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.users_set_password)) },
                        leadingIcon = { Icon(Icons.Filled.Key, null) },
                        onClick = { menu = false; onSetPassword() },
                    )
                    if (!isSelf) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_delete)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun CreateUserDialog(
    onConfirm: (String, String, Role) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.USER) }
    val valid = username.isNotBlank() && password.length >= 8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.users_new_user)) },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.common_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.users_password_min8)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Column(Modifier.padding(top = 8.dp)) {
                    RoleOption(stringResource(R.string.role_user), role == Role.USER) { role = Role.USER }
                    RoleOption(stringResource(R.string.role_admin), role == Role.ADMIN) { role = Role.ADMIN }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(username, password, role) }, enabled = valid) { Text(stringResource(R.string.common_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun RoleOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SetPasswordDialog(
    username: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.users_set_password)) },
        text = {
            Column {
                Text(stringResource(R.string.users_set_password_desc, username), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.users_password_min8)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.length >= 8) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
