package eu.r3pr3ss10n.hangar.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.domain.FileItem
import eu.r3pr3ss10n.hangar.domain.Folder
import eu.r3pr3ss10n.hangar.domain.User
import eu.r3pr3ss10n.hangar.ui.DownloadViewModel
import eu.r3pr3ss10n.hangar.ui.PrefsViewModel
import eu.r3pr3ss10n.hangar.ui.account.AboutScreen
import eu.r3pr3ss10n.hangar.ui.account.AccountScreen
import eu.r3pr3ss10n.hangar.ui.admin.TelegramScreen
import eu.r3pr3ss10n.hangar.ui.admin.UsersScreen
import eu.r3pr3ss10n.hangar.ui.drive.DriveScreen
import eu.r3pr3ss10n.hangar.ui.drive.MovePickerSheet
import eu.r3pr3ss10n.hangar.ui.drive.SheetTarget
import eu.r3pr3ss10n.hangar.ui.myshares.MySharesScreen
import eu.r3pr3ss10n.hangar.ui.preview.PreviewScreen
import eu.r3pr3ss10n.hangar.ui.search.SearchScreen
import eu.r3pr3ss10n.hangar.ui.shared.SharedScreen
import eu.r3pr3ss10n.hangar.ui.sharing.GrantSheet
import eu.r3pr3ss10n.hangar.ui.sharing.ShareLinkSheet
import eu.r3pr3ss10n.hangar.ui.tags.AssignTagsSheet
import eu.r3pr3ss10n.hangar.ui.tags.TagItemsScreen
import eu.r3pr3ss10n.hangar.ui.tags.TagsScreen

/** A modal sheet the drive flow can raise for a file/folder action. */
private sealed interface ActiveSheet {
    data class ShareLink(val file: FileItem) : ActiveSheet
    data class ShareUser(val target: SheetTarget) : ActiveSheet
    data class Tags(val target: SheetTarget) : ActiveSheet
    data class Move(val target: SheetTarget) : ActiveSheet
}

/**
 * HangarNavHost wires every authenticated destination and hosts the cross-cutting
 * action sheets (share link, share with user, tags, move) that the drive screens
 * raise via callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangarNavHost(
    navController: NavHostController,
    user: User,
    onLoggedOut: () -> Unit,
    onForgetServer: () -> Unit,
    onRequestUpload: (folderId: String?) -> Unit,
    modifier: Modifier = Modifier,
    prefs: PrefsViewModel = hiltViewModel(),
    downloads: DownloadViewModel = hiltViewModel(),
) {
    val gridView by prefs.gridView.collectAsStateWithLifecycle()
    val downloadItems by downloads.items.collectAsStateWithLifecycle()
    // fileId -> progress (0..1) for files currently downloading, so rows can show
    // a live spinner in place of their icon.
    val downloadProgress = downloadItems
        .filter { it.status == eu.r3pr3ss10n.hangar.data.download.DownloadStatus.DOWNLOADING }
        .associate { it.fileId to it.progress }
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }

    fun openFolder(folder: Folder) = navController.navigate(Routes.folder(folder.id, folder.name))
    fun openFile(file: FileItem) {
        if (file.isImage) navController.navigate(Routes.preview(file.id))
        else downloads.download(file)
    }

    fun driveCallbacks() = DriveActions(
        onManageTags = { activeSheet = ActiveSheet.Tags(it) },
        onShareLink = { activeSheet = ActiveSheet.ShareLink(it) },
        onShareUser = { activeSheet = ActiveSheet.ShareUser(it) },
        onDownload = { downloads.download(it) },
        onMove = { activeSheet = ActiveSheet.Move(it) },
    )

    NavHost(
        navController = navController,
        startDestination = Routes.FILES,
        modifier = modifier,
        // No animated transitions between destinations — instant swaps.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Routes.FILES) {
            val cb = driveCallbacks()
            DriveScreen(
                title = stringResource(R.string.nav_files),
                parentId = null,
                isRoot = true,
                gridView = gridView,
                onToggleView = prefs::toggleGridView,
                onOpenFolder = ::openFolder,
                onOpenFile = ::openFile,
                onSearch = { navController.navigate(Routes.SEARCH) },
                onBack = null,
                onUpload = onRequestUpload,
                onManageTags = cb.onManageTags,
                onShareLink = cb.onShareLink,
                onShareUser = cb.onShareUser,
                onDownload = cb.onDownload,
                onMove = cb.onMove,
                downloadProgress = downloadProgress,
            )
        }

        composable(
            Routes.FOLDER_PATTERN,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val name = entry.arguments?.getString("name").orEmpty()
            val cb = driveCallbacks()
            DriveScreen(
                title = name.ifEmpty { stringResource(R.string.nav_folder_fallback) },
                parentId = id,
                isRoot = false,
                gridView = gridView,
                onToggleView = prefs::toggleGridView,
                onOpenFolder = ::openFolder,
                onOpenFile = ::openFile,
                onSearch = { navController.navigate(Routes.SEARCH) },
                onBack = { navController.popBackStack() },
                onUpload = onRequestUpload,
                onManageTags = cb.onManageTags,
                onShareLink = cb.onShareLink,
                onShareUser = cb.onShareUser,
                onDownload = cb.onDownload,
                onMove = cb.onMove,
                downloadProgress = downloadProgress,
            )
        }

        composable(Routes.SHARED) {
            SharedScreen(
                title = stringResource(R.string.nav_shared_with_me),
                isRoot = true,
                onBack = null,
                onOpenFolder = { navController.navigate(Routes.sharedFolder(it.id, it.name)) },
                onOpenFile = ::openFile,
            )
        }

        composable(
            Routes.SHARED_FOLDER_PATTERN,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val name = entry.arguments?.getString("name").orEmpty()
            SharedScreen(
                title = name.ifEmpty { stringResource(R.string.nav_shared_folder_fallback) },
                isRoot = false,
                onBack = { navController.popBackStack() },
                onOpenFolder = { navController.navigate(Routes.sharedFolder(it.id, it.name)) },
                onOpenFile = ::openFile,
            )
        }

        composable(Routes.TAGS) {
            TagsScreen(onOpenTag = { navController.navigate(Routes.tagItems(it.id, it.name)) })
        }

        composable(
            Routes.TAG_ITEMS_PATTERN,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val name = entry.arguments?.getString("name").orEmpty()
            TagItemsScreen(
                title = name.ifEmpty { stringResource(R.string.nav_tag_fallback) },
                onBack = { navController.popBackStack() },
                onOpenFolder = ::openFolder,
                onOpenFile = ::openFile,
            )
        }

        composable(Routes.ACCOUNT) {
            AccountScreen(
                user = user,
                onOpenMyLinks = { navController.navigate(Routes.MY_LINKS) },
                onOpenUsers = { navController.navigate(Routes.ADMIN_USERS) },
                onOpenTelegram = { navController.navigate(Routes.ADMIN_TELEGRAM) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onLoggedOut = onLoggedOut,
                onForgetServer = onForgetServer,
            )
        }

        composable(Routes.MY_LINKS) {
            MySharesScreen(
                onBack = { navController.popBackStack() },
                onOpenFile = ::openFile,
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenFolder = ::openFolder,
                onOpenFile = ::openFile,
            )
        }

        composable(
            Routes.PREVIEW_PATTERN,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            PreviewScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ADMIN_USERS) {
            UsersScreen(currentUserId = user.id, onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_TELEGRAM) {
            TelegramScreen(onBack = { navController.popBackStack() })
        }
    }

    // ---- cross-cutting action sheets ----
    when (val sheet = activeSheet) {
        is ActiveSheet.ShareLink -> ModalBottomSheet(onDismissRequest = { activeSheet = null }) {
            ShareLinkSheet(fileId = sheet.file.id, fileName = sheet.file.name)
        }

        is ActiveSheet.ShareUser -> {
            val (id, isFolder, name) = sheet.target.idKindName()
            ModalBottomSheet(onDismissRequest = { activeSheet = null }) {
                GrantSheet(id = id, isFolder = isFolder, name = name)
            }
        }

        is ActiveSheet.Tags -> {
            val (id, isFolder, name) = sheet.target.idKindName()
            ModalBottomSheet(onDismissRequest = { activeSheet = null }) {
                AssignTagsSheet(itemId = id, isFolder = isFolder, name = name)
            }
        }

        is ActiveSheet.Move -> {
            val (id, isFolder, _) = sheet.target.idKindName()
            ModalBottomSheet(onDismissRequest = { activeSheet = null }) {
                MovePickerSheet(
                    movingId = id,
                    isFolder = isFolder,
                    onPicked = { activeSheet = null },
                    onDismiss = { activeSheet = null },
                )
            }
        }

        null -> Unit
    }
}

private data class DriveActions(
    val onManageTags: (SheetTarget) -> Unit,
    val onShareLink: (FileItem) -> Unit,
    val onShareUser: (SheetTarget) -> Unit,
    val onDownload: (FileItem) -> Unit,
    val onMove: (SheetTarget) -> Unit,
)

private fun SheetTarget.idKindName(): Triple<String, Boolean, String> = when (this) {
    is SheetTarget.FileTarget -> Triple(file.id, false, file.name)
    is SheetTarget.FolderTarget -> Triple(folder.id, true, folder.name)
}
