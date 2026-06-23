package eu.r3pr3ss10n.hangar.ui.nav

// Navigation route constants for the authenticated app graph. The pre-auth flow
// (connect / login / setup-notice) is driven by AppState, not these routes.
object Routes {
    const val FILES = "files"
    const val SHARED = "shared"
    const val TAGS = "tags"
    const val ACCOUNT = "account"

    // Detail destinations
    const val SEARCH = "search"
    const val MY_LINKS = "myLinks"
    const val ABOUT = "about"
    const val ADMIN_USERS = "admin/users"
    const val ADMIN_TELEGRAM = "admin/telegram"

    // Parameterised
    fun folder(id: String, name: String) = "folder/$id?name=${name}"
    const val FOLDER_PATTERN = "folder/{id}?name={name}"

    fun tagItems(id: String, name: String) = "tagItems/$id?name=${name}"
    const val TAG_ITEMS_PATTERN = "tagItems/{id}?name={name}"

    fun preview(id: String) = "preview/$id"
    const val PREVIEW_PATTERN = "preview/{id}"

    fun sharedFolder(id: String, name: String) = "sharedFolder/$id?name=${name}"
    const val SHARED_FOLDER_PATTERN = "sharedFolder/{id}?name={name}"
}
