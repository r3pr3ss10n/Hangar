package eu.r3pr3ss10n.hangar.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.domain.User
import eu.r3pr3ss10n.hangar.ui.download.DownloadDock
import eu.r3pr3ss10n.hangar.ui.nav.HangarNavHost
import eu.r3pr3ss10n.hangar.ui.nav.Routes
import eu.r3pr3ss10n.hangar.ui.upload.UploadDock
import eu.r3pr3ss10n.hangar.ui.upload.UploadViewModel

private data class TopLevelDest(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelDests = listOf(
    TopLevelDest(Routes.FILES, R.string.nav_files, Icons.Filled.Folder),
    TopLevelDest(Routes.SHARED, R.string.nav_shared, Icons.Filled.People),
    TopLevelDest(Routes.TAGS, R.string.nav_tags, Icons.AutoMirrored.Filled.Label),
    TopLevelDest(Routes.ACCOUNT, R.string.nav_account, Icons.Filled.Person),
)

/**
 * MainShell hosts the authenticated app: a bottom navigation bar over the four
 * top-level sections, the upload dock, and a NavHost for content plus detail
 * destinations. It owns the file-picker launcher so an upload can start from any
 * folder level.
 */
@Composable
fun MainShell(
    user: User,
    onLoggedOut: () -> Unit,
    onForgetServer: () -> Unit,
    uploads: UploadViewModel = hiltViewModel(),
    downloads: DownloadViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val uploadItems by uploads.items.collectAsState()
    val downloadItems by downloads.items.collectAsState()

    // The folder an upload should target; set when the user taps "Upload".
    var pendingUploadFolder by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) uploads.upload(uris, pendingUploadFolder)
    }

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDest = backStackEntry?.destination
            val onTopLevel = topLevelDests.any { dest ->
                currentDest?.hierarchy?.any { it.route == dest.route } == true
            }
            Column {
                DownloadDock(
                    items = downloadItems,
                    onOpen = { downloads.open(it) },
                    onDismiss = downloads::clearFinished,
                )
                UploadDock(
                    items = uploadItems,
                    onDismiss = uploads::clearFinished,
                    onCancel = uploads::cancel,
                )
                if (onTopLevel) {
                    NavigationBar {
                        topLevelDests.forEach { dest ->
                            val selected = currentDest?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                                label = { Text(stringResource(dest.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        // Only consume the bottom inset (nav bar + docks) here; each screen's own
        // TopAppBar handles the status-bar inset, so passing the full padding
        // would double the top gap.
        HangarNavHost(
            navController = navController,
            user = user,
            onLoggedOut = onLoggedOut,
            onForgetServer = onForgetServer,
            onRequestUpload = { folderId ->
                pendingUploadFolder = folderId
                picker.launch(arrayOf("*/*"))
            },
            downloads = downloads,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        )
    }
}
