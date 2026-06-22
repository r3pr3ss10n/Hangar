/**
 * UI message catalogue. Keys are flat, dot-namespaced strings; a value is either
 * a plain string (with optional `{name}` placeholders) or a function of the
 * interpolation params — the function form lets a locale apply its own plural
 * rules. Look-ups go through `t()` in composables/useI18n.ts, which falls back
 * to the English string when a key is missing from the active locale.
 */
export type Locale = "en" | "ru";

export type MsgParams = Record<string, string | number>;
type Msg = string | ((p: MsgParams) => string);
type Table = Record<string, Msg>;

/**
 * Locales offered in the settings picker, in display order. `flag` is a regional
 * emoji shown alongside the name. Add a row here (plus a message table below) to
 * offer another language.
 */
export const LOCALES: { code: Locale; label: string; flag: string }[] = [
  { code: "en", label: "English", flag: "🇬🇧" },
  { code: "ru", label: "Русский", flag: "🇷🇺" },
];

/**
 * Russian plural selection: returns the form for `one` (1, 21, …), `few`
 * (2–4, 22–24, …), or `many` (0, 5–20, …) based on the standard rules.
 */
function ru(n: number, one: string, few: string, many: string): string {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 10 || m100 >= 20)) return few;
  return many;
}

const en: Table = {
  // common
  "common.cancel": "Cancel",
  "common.delete": "Delete",
  "common.save": "Save",
  "common.saving": "Saving…",
  "common.create": "Create",
  "common.creating": "Creating…",
  "common.deleting": "Deleting…",
  "common.name": "Name",
  "common.username": "Username",
  "common.password": "Password",
  "role.user": "User",
  "role.admin": "Admin",

  // navigation / sidebar
  "nav.myDrive": "My Drive",
  "nav.settings": "Settings",
  "nav.users": "Users",
  "nav.telegram": "Telegram",
  "nav.admin": "Admin",
  "sidebar.storageUsed": (p) => `${p.size} used`,

  // topbar
  "topbar.search": "Search…",
  "topbar.toggleMenu": "Toggle menu",
  "topbar.searchAria": "Search",
  "topbar.logout": "Log out",

  // theme
  "theme.toggle": "Toggle theme",
  "theme.switchToLight": "Switch to light",
  "theme.switchToDark": "Switch to dark",

  // settings
  "settings.title": "Settings",
  "settings.subtitle": "Manage your Hangar preferences.",
  "settings.couldNotLoad": "Could not load settings",
  "settings.couldNotSave": "Could not save",
  "settings.interfaceLanguage": "Interface language",
  "settings.interfaceLanguageDesc":
    "The selected language is applied immediately and remembered on this device.",
  "settings.generateThumbnails": "Generate image thumbnails",
  "settings.generateThumbnailsDesc":
    "When on, image uploads get a small preview thumbnail shown in the grid view and the " +
    "preview modal. Turning it off saves a little database space; existing thumbnails are kept.",

  // login
  "login.welcome": "Welcome back",
  "login.subtitle": "Sign in to your Hangar",
  "login.signIn": "Sign in",
  "login.signingIn": "Signing in…",
  "login.invalid": "Invalid username or password",

  // setup
  "setup.title": "Set up Hangar",
  "setup.subtitle": "Create the first administrator account.",
  "setup.atLeast8": "At least 8 characters.",
  "setup.confirmPassword": "Confirm password",
  "setup.create": "Create admin & continue",
  "setup.passwordsNoMatch": "Passwords do not match",
  "setup.passwordTooShort": "Password must be at least 8 characters",
  "setup.failed": "Setup failed",

  // onboarding
  "onboarding.step": "Step 2 of 2",
  "onboarding.telegram.title": "Connect Telegram",
  "onboarding.telegram.subtitle":
    "Link a Telegram account — it's where every uploaded file is stored. Hangar can't store files without it.",
  "onboarding.telegram.note":
    "Hangar connects directly to Telegram's official API. Your phone and password are never stored — only an encrypted session kept on your own server.",

  // drive (index)
  "drive.folderCount": (p) => `${p.n} folder${p.n === 1 ? "" : "s"}`,
  "drive.fileCount": (p) => `${p.n} file${p.n === 1 ? "" : "s"}`,
  "drive.newFolder": "New folder",
  "drive.upload": "Upload",
  "drive.uploadFiles": "Upload files",
  "drive.listView": "List view",
  "drive.gridView": "Grid view",
  "drive.emptyTitle": "This folder is empty",
  "drive.emptyDesc":
    "Drag files anywhere on the page, or use the buttons above to get started.",
  "drive.colName": "Name",
  "drive.colSize": "Size",
  "drive.colModified": "Modified",
  "drive.colActions": "Actions",
  "drive.folderActions": "Folder actions",
  "drive.fileActions": "File actions",
  "drive.open": "Open",
  "drive.rename": "Rename",
  "drive.move": "Move",
  "drive.download": "Download",
  "drive.delete": "Delete",
  "drive.newFolderTitle": "New folder",
  "drive.newFolderDesc": (p) => `Create a folder in ${p.name}.`,
  "drive.untitledFolder": "Untitled folder",
  "drive.renameFolderTitle": "Rename folder",
  "drive.renameFileTitle": "Rename file",
  "drive.moveTitle": (p) => `Move “${p.name}”`,
  "drive.moveDesc": "Choose a destination folder.",
  "drive.moveRoot": "My Drive (root)",
  "drive.moveHere": "Move here",
  "drive.moving": "Moving…",
  "drive.noOtherFolders":
    "No other top-level folders. You can move this to the root.",
  "drive.deleteFolderTitle": "Delete folder?",
  "drive.deleteFileTitle": "Delete file?",
  "drive.deleteFolderDesc": (p) =>
    `“${p.name}” and all of its contents will be permanently deleted. This cannot be undone.`,
  "drive.deleteFileDesc": (p) =>
    `“${p.name}” will be permanently deleted. This cannot be undone.`,
  "drive.couldNotCreateFolder": "Could not create folder",
  "drive.renameFailed": "Rename failed",
  "drive.deleteFailed": "Delete failed",
  "drive.moveFailed": "Move failed",
  "drive.couldNotLoadDestinations": "Could not load destinations",

  // drag overlay
  "drag.dropToUpload": "Drop files to upload",
  "drag.toFolder": (p) => `to ${p.name}`,

  // upload dock
  "upload.uploadingN": (p) => `Uploading ${p.n} file${p.n === 1 ? "" : "s"}…`,
  "upload.uploads": "Uploads",
  "upload.canceled": "Canceled",
  "upload.collapse": "Collapse",
  "upload.clear": "Clear",
  "upload.cancelUpload": "Cancel upload",

  // file preview
  "preview.previous": "Previous",
  "preview.next": "Next",
  "preview.couldNotLoad": "Could not load this image.",
  "preview.download": "Download",

  // command palette
  "cmd.searchPlaceholder": "Search files, run a command…",
  "cmd.searching": "Searching…",
  "cmd.noResults": (p) => `No results for “${p.q}”`,
  "cmd.section.navigation": "Navigation",
  "cmd.section.actions": "Actions",
  "cmd.section.folders": "Folders",
  "cmd.section.files": "Files",
  "cmd.goToDrive": "Go to My Drive",
  "cmd.goToSettings": "Go to Settings",
  "cmd.uploadFiles": "Upload files",
  "cmd.uploadHint": "Pick files to upload here",
  "cmd.newFolder": "New folder",
  "cmd.manageUsers": "Manage users",
  "cmd.telegramStorage": "Telegram storage",

  // store toasts (drive)
  "store.folderCreated": (p) => `Folder “${p.name}” created`,
  "store.folderRenamed": "Folder renamed",
  "store.fileRenamed": "File renamed",
  "store.deleted": (p) => `Deleted “${p.name}”`,
  "store.moved": "Moved",
  "store.uploadsFailed": (p) => `${p.n} upload${p.n === 1 ? "" : "s"} failed`,
  "store.uploadFailed": "Upload failed",
  "store.loadFolderFailed": "Failed to load folder",

  // users admin
  "users.title": "Users",
  "users.subtitle": "Manage who can access this Hangar.",
  "users.newUser": "New user",
  "users.colUser": "User",
  "users.colRole": "Role",
  "users.colCreated": "Created",
  "users.you": "(you)",
  "users.actions": "User actions",
  "users.setPassword": "Set password",
  "users.createTitle": "New user",
  "users.createDesc": "Create an account and assign a role.",
  "users.role": "Role",
  "users.createUser": "Create user",
  "users.setPasswordTitle": "Set password",
  "users.setPasswordDesc": (p) => `New password for ${p.name}.`,
  "users.updatePassword": "Update password",
  "users.deleteTitle": "Delete user?",
  "users.deleteDesc": (p) =>
    `“${p.name}” will be permanently removed. This cannot be undone.`,
  "users.failedLoad": "Failed to load users",
  "users.created": (p) => `User “${p.name}” created`,
  "users.failedCreate": "Failed to create user",
  "users.checkForm": "Check the form",
  "users.checkFormDesc":
    "Username is required and password must be at least 8 characters.",
  "users.passwordUpdated": (p) => `Password updated for ${p.name}`,
  "users.passwordTooShort": "Password too short",
  "users.mustBe8": "Must be at least 8 characters.",
  "users.failedSetPassword": "Failed to set password",
  "users.deleted": (p) => `Deleted ${p.name}`,
  "users.failedDelete": "Failed to delete user",

  // telegram admin
  "tg.title": "Telegram storage",
  "tg.subtitle":
    "Link a Telegram account — it becomes the backend that stores every uploaded file.",
  "tg.status.linked": "Linked",
  "tg.status.linking": "Linking",
  "tg.status.not_linked": "Not linked",
  "tg.active": "Active",
  "tg.premium": "Premium",
  "tg.readyToStore": "Ready to store files",
  "tg.noAccountLinked": "No account linked yet",
  "tg.unlink": "Unlink",
  "tg.unlinking": "Unlinking…",
  "tg.unlinkWarning":
    "Don't unlink this account unless you mean to. Files live in this account's private channel — " +
    "unlinking makes every uploaded file inaccessible until the same account is linked again.",
  "tg.linkAccount": "Link an account",
  "tg.linkAccountDesc": "You'll receive a login code in your Telegram app.",
  "tg.step.phone": "Phone",
  "tg.step.code": "Code",
  "tg.step.password": "2FA",
  "tg.phoneNumber": "Phone number",
  "tg.country": "Country",
  "tg.selectCountry": "Select country",
  "tg.searchCountry": "Search",
  "tg.noCountry": "No country found",
  "tg.sendCode": "Send code",
  "tg.sending": "Sending…",
  "tg.loginCode": "Login code",
  "tg.codeHint": "Telegram sent a code to your account.",
  "tg.verifyCode": "Verify code",
  "tg.verifying": "Verifying…",
  "tg.twoFactorPassword": "Two-factor password",
  "tg.twoFactorHint": "This account has 2FA enabled.",
  "tg.completeLink": "Complete link",
  "tg.unlinkTitle": "Unlink Telegram account?",
  "tg.unlinkDesc":
    "Existing files will become inaccessible until the account is re-linked.",
  "tg.failedLoadStatus": "Failed to load status",
  "tg.enterPhone":
    "Enter a phone number in international format, e.g. +15551234567",
  "tg.failedStartLink": "Failed to start link",
  "tg.enterCode": "Enter the login code from Telegram",
  "tg.invalidCode": "Invalid code",
  "tg.enterPassword": "Enter your 2FA password",
  "tg.invalidPassword": "Invalid 2FA password",
  "tg.accountLinked": "Telegram account linked",
  "tg.accountUnlinked": "Telegram account unlinked",
  "tg.failedUnlink": "Failed to unlink",

  // share links
  "share.action": "Share link",
  "share.title": "Share file",
  "share.subtitle": "Anyone with the link can view and download this file.",
  "share.duration": "Link expires",
  "share.create": "Create link",
  "share.activeLinks": "Active links",
  "share.noLinks": "No share links yet.",
  "share.copy": "Copy link",
  "share.copied": "Link copied",
  "share.copyFailed": "Could not copy link",
  "share.revoke": "Revoke",
  "share.revoked": "Link revoked",
  "share.expiresNever": "Never expires",
  "share.expiresOn": (p) => `Expires ${p.date}`,
  "share.expired": "Expired",
  "share.createFailed": "Could not create link",
  "share.loadFailed": "Could not load links",
  "share.revokeFailed": "Could not revoke link",
  "share.dur.1h": "1 hour",
  "share.dur.24h": "24 hours",
  "share.dur.7d": "7 days",
  "share.dur.30d": "30 days",
  "share.dur.never": "No expiry",
  "sharePage.loading": "Loading…",
  "sharePage.download": "Download",
  "sharePage.notFoundTitle": "Link unavailable",
  "sharePage.notFoundDesc": "This share link is invalid or has expired.",
  "sharePage.sharedVia": "Shared via Hangar",
  "sharePage.expiresOn": (p) => `Link expires ${p.date}`,

  // navigation
  "nav.shared": "Shared with me",

  // internal access grants (user-to-user)
  "grant.action": "Share with people",
  "grant.title": "Share with people",
  "grant.subtitle": "Give other users of this Hangar view access.",
  "grant.addPeople": "Add people",
  "grant.searchPlaceholder": "Search users…",
  "grant.noUsers": "No other users to share with.",
  "grant.noMatches": "No matching users.",
  "grant.peopleWithAccess": "People with access",
  "grant.noGrants": "Not shared with anyone yet.",
  "grant.viewer": "Viewer",
  "grant.revoke": "Remove",
  "grant.granted": "Access granted",
  "grant.revoked": "Access removed",
  "grant.loadFailed": "Could not load sharing info",
  "grant.grantFailed": "Could not share",
  "grant.revokeFailed": "Could not remove access",

  // shared-with-me page
  "shared.title": "Shared with me",
  "shared.subtitle": "Files and folders other people shared with you.",
  "shared.emptyTitle": "Nothing shared with you yet",
  "shared.emptyDesc": "When someone shares a file or folder, it shows up here.",
  "shared.loadFailed": "Could not load shared items",
  "shared.sharedBy": (p) => `from ${p.name}`,

  // navigation
  "nav.tags": "Tags",

  // tags
  "tag.action": "Tags",
  "tag.title": "Tags",
  "tag.none": "No tags yet — create one below.",
  "tag.newPlaceholder": "New tag name",
  "tag.create": "Create",
  "tag.delete": "Delete tag",
  "tag.untag": "Remove tag",
  "tag.assignFailed": "Could not update tags",
  "tag.createFailed": "Could not create tag",
  "tag.deleteFailed": "Could not delete tag",
  "tag.loadFailed": "Could not load tag",
  "tag.emptyTitle": "Nothing tagged with this yet",
};

const ruTable: Table = {
  // common
  "common.cancel": "Отмена",
  "common.delete": "Удалить",
  "common.save": "Сохранить",
  "common.saving": "Сохранение…",
  "common.create": "Создать",
  "common.creating": "Создание…",
  "common.deleting": "Удаление…",
  "common.name": "Название",
  "common.username": "Имя пользователя",
  "common.password": "Пароль",
  "role.user": "Пользователь",
  "role.admin": "Админ",

  // navigation / sidebar
  "nav.myDrive": "Мой диск",
  "nav.settings": "Настройки",
  "nav.users": "Пользователи",
  "nav.telegram": "Telegram",
  "nav.admin": "Администрирование",
  "sidebar.storageUsed": (p) => `${p.size} занято`,

  // topbar
  "topbar.search": "Поиск…",
  "topbar.toggleMenu": "Открыть меню",
  "topbar.searchAria": "Поиск",
  "topbar.logout": "Выйти",

  // theme
  "theme.toggle": "Переключить тему",
  "theme.switchToLight": "Светлая тема",
  "theme.switchToDark": "Тёмная тема",

  // settings
  "settings.title": "Настройки",
  "settings.subtitle": "Управление настройками Hangar.",
  "settings.couldNotLoad": "Не удалось загрузить настройки",
  "settings.couldNotSave": "Не удалось сохранить",
  "settings.interfaceLanguage": "Язык интерфейса",
  "settings.interfaceLanguageDesc":
    "Выбранный язык применяется сразу и запоминается на этом устройстве.",
  "settings.generateThumbnails": "Создавать миниатюры изображений",
  "settings.generateThumbnailsDesc":
    "Когда включено, для загруженных изображений создаётся небольшая миниатюра, " +
    "отображаемая в режиме сетки и в окне просмотра. Отключение немного экономит место в " +
    "базе данных; существующие миниатюры сохраняются.",

  // login
  "login.welcome": "С возвращением",
  "login.subtitle": "Войдите в Hangar",
  "login.signIn": "Войти",
  "login.signingIn": "Вход…",
  "login.invalid": "Неверное имя пользователя или пароль",

  // setup
  "setup.title": "Настройка Hangar",
  "setup.subtitle": "Создайте первую учётную запись администратора.",
  "setup.atLeast8": "Не менее 8 символов.",
  "setup.confirmPassword": "Подтвердите пароль",
  "setup.create": "Создать администратора и продолжить",
  "setup.passwordsNoMatch": "Пароли не совпадают",
  "setup.passwordTooShort": "Пароль должен содержать не менее 8 символов",
  "setup.failed": "Не удалось выполнить настройку",

  // onboarding
  "onboarding.step": "Шаг 2 из 2",
  "onboarding.telegram.title": "Подключите Telegram",
  "onboarding.telegram.subtitle":
    "Подключите аккаунт Telegram — в нём хранятся все загруженные файлы. Без него Hangar не сможет хранить файлы.",
  "onboarding.telegram.note":
    "Hangar подключается напрямую к официальному API Telegram. Ваш номер и пароль нигде не сохраняются — на вашем сервере остаётся только зашифрованная сессия.",

  // drive (index)
  "drive.folderCount": (p) =>
    `${p.n} ${ru(Number(p.n), "папка", "папки", "папок")}`,
  "drive.fileCount": (p) =>
    `${p.n} ${ru(Number(p.n), "файл", "файла", "файлов")}`,
  "drive.newFolder": "Новая папка",
  "drive.upload": "Загрузить",
  "drive.uploadFiles": "Загрузить файлы",
  "drive.listView": "Список",
  "drive.gridView": "Сетка",
  "drive.emptyTitle": "Эта папка пуста",
  "drive.emptyDesc":
    "Перетащите файлы в любое место страницы или воспользуйтесь кнопками ниже, чтобы начать.",
  "drive.colName": "Название",
  "drive.colSize": "Размер",
  "drive.colModified": "Изменён",
  "drive.colActions": "Действия",
  "drive.folderActions": "Действия с папкой",
  "drive.fileActions": "Действия с файлом",
  "drive.open": "Открыть",
  "drive.rename": "Переименовать",
  "drive.move": "Переместить",
  "drive.download": "Скачать",
  "drive.delete": "Удалить",
  "drive.newFolderTitle": "Новая папка",
  "drive.newFolderDesc": (p) => `Создать папку в «${p.name}».`,
  "drive.untitledFolder": "Папка без названия",
  "drive.renameFolderTitle": "Переименовать папку",
  "drive.renameFileTitle": "Переименовать файл",
  "drive.moveTitle": (p) => `Переместить «${p.name}»`,
  "drive.moveDesc": "Выберите папку назначения.",
  "drive.moveRoot": "Мой диск (корень)",
  "drive.moveHere": "Переместить сюда",
  "drive.moving": "Перемещение…",
  "drive.noOtherFolders":
    "Других папок верхнего уровня нет. Вы можете переместить это в корень.",
  "drive.deleteFolderTitle": "Удалить папку?",
  "drive.deleteFileTitle": "Удалить файл?",
  "drive.deleteFolderDesc": (p) =>
    `«${p.name}» и всё её содержимое будут безвозвратно удалены. Это действие нельзя отменить.`,
  "drive.deleteFileDesc": (p) =>
    `«${p.name}» будет безвозвратно удалён. Это действие нельзя отменить.`,
  "drive.couldNotCreateFolder": "Не удалось создать папку",
  "drive.renameFailed": "Не удалось переименовать",
  "drive.deleteFailed": "Не удалось удалить",
  "drive.moveFailed": "Не удалось переместить",
  "drive.couldNotLoadDestinations": "Не удалось загрузить папки назначения",

  // drag overlay
  "drag.dropToUpload": "Перетащите файлы для загрузки",
  "drag.toFolder": (p) => `в «${p.name}»`,

  // upload dock
  "upload.uploadingN": (p) =>
    `Загрузка ${p.n} ${ru(Number(p.n), "файла", "файлов", "файлов")}…`,
  "upload.uploads": "Загрузки",
  "upload.canceled": "Отменено",
  "upload.collapse": "Свернуть",
  "upload.clear": "Очистить",
  "upload.cancelUpload": "Отменить загрузку",

  // file preview
  "preview.previous": "Назад",
  "preview.next": "Вперёд",
  "preview.couldNotLoad": "Не удалось загрузить изображение.",
  "preview.download": "Скачать",

  // command palette
  "cmd.searchPlaceholder": "Поиск файлов, выполнение команд…",
  "cmd.searching": "Поиск…",
  "cmd.noResults": (p) => `Нет результатов по запросу «${p.q}»`,
  "cmd.section.navigation": "Навигация",
  "cmd.section.actions": "Действия",
  "cmd.section.folders": "Папки",
  "cmd.section.files": "Файлы",
  "cmd.goToDrive": "Перейти в Мой диск",
  "cmd.goToSettings": "Перейти в Настройки",
  "cmd.uploadFiles": "Загрузить файлы",
  "cmd.uploadHint": "Выберите файлы для загрузки",
  "cmd.newFolder": "Новая папка",
  "cmd.manageUsers": "Управление пользователями",
  "cmd.telegramStorage": "Хранилище Telegram",

  // store toasts (drive)
  "store.folderCreated": (p) => `Папка «${p.name}» создана`,
  "store.folderRenamed": "Папка переименована",
  "store.fileRenamed": "Файл переименован",
  "store.deleted": (p) => `Удалено «${p.name}»`,
  "store.moved": "Перемещено",
  "store.uploadsFailed": (p) =>
    `${p.n} ${ru(Number(p.n), "загрузка", "загрузки", "загрузок")} не удалось`,
  "store.uploadFailed": "Не удалось загрузить",
  "store.loadFolderFailed": "Не удалось загрузить папку",

  // users admin
  "users.title": "Пользователи",
  "users.subtitle": "Управление доступом к этому Hangar.",
  "users.newUser": "Новый пользователь",
  "users.colUser": "Пользователь",
  "users.colRole": "Роль",
  "users.colCreated": "Создан",
  "users.you": "(вы)",
  "users.actions": "Действия с пользователем",
  "users.setPassword": "Задать пароль",
  "users.createTitle": "Новый пользователь",
  "users.createDesc": "Создайте учётную запись и назначьте роль.",
  "users.role": "Роль",
  "users.createUser": "Создать пользователя",
  "users.setPasswordTitle": "Задать пароль",
  "users.setPasswordDesc": (p) => `Новый пароль для ${p.name}.`,
  "users.updatePassword": "Обновить пароль",
  "users.deleteTitle": "Удалить пользователя?",
  "users.deleteDesc": (p) =>
    `«${p.name}» будет безвозвратно удалён. Это действие нельзя отменить.`,
  "users.failedLoad": "Не удалось загрузить пользователей",
  "users.created": (p) => `Пользователь «${p.name}» создан`,
  "users.failedCreate": "Не удалось создать пользователя",
  "users.checkForm": "Проверьте форму",
  "users.checkFormDesc":
    "Имя пользователя обязательно, пароль — не менее 8 символов.",
  "users.passwordUpdated": (p) => `Пароль обновлён для ${p.name}`,
  "users.passwordTooShort": "Слишком короткий пароль",
  "users.mustBe8": "Не менее 8 символов.",
  "users.failedSetPassword": "Не удалось задать пароль",
  "users.deleted": (p) => `Удалён ${p.name}`,
  "users.failedDelete": "Не удалось удалить пользователя",

  // telegram admin
  "tg.title": "Хранилище Telegram",
  "tg.subtitle":
    "Подключите аккаунт Telegram — он станет хранилищем для всех загруженных файлов.",
  "tg.status.linked": "Подключено",
  "tg.status.linking": "Подключение",
  "tg.status.not_linked": "Не подключено",
  "tg.active": "Активно",
  "tg.premium": "Premium",
  "tg.readyToStore": "Готово к хранению файлов",
  "tg.noAccountLinked": "Аккаунт ещё не подключён",
  "tg.unlink": "Отключить",
  "tg.unlinking": "Отключение…",
  "tg.unlinkWarning":
    "Не отключайте этот аккаунт без необходимости. Файлы хранятся в приватном канале этого аккаунта — " +
    "после отключения все загруженные файлы станут недоступны, пока не подключите тот же аккаунт снова.",
  "tg.linkAccount": "Подключить аккаунт",
  "tg.linkAccountDesc": "Вы получите код входа в приложении Telegram.",
  "tg.step.phone": "Телефон",
  "tg.step.code": "Код",
  "tg.step.password": "2FA",
  "tg.phoneNumber": "Номер телефона",
  "tg.country": "Страна",
  "tg.selectCountry": "Выберите страну",
  "tg.searchCountry": "Поиск",
  "tg.noCountry": "Страна не найдена",
  "tg.sendCode": "Отправить код",
  "tg.sending": "Отправка…",
  "tg.loginCode": "Код входа",
  "tg.codeHint": "Telegram отправил код в ваш аккаунт.",
  "tg.verifyCode": "Проверить код",
  "tg.verifying": "Проверка…",
  "tg.twoFactorPassword": "Пароль двухфакторной аутентификации",
  "tg.twoFactorHint": "Для этого аккаунта включена 2FA.",
  "tg.completeLink": "Завершить подключение",
  "tg.unlinkTitle": "Отключить аккаунт Telegram?",
  "tg.unlinkDesc":
    "Существующие файлы станут недоступны, пока аккаунт не будет подключён снова.",
  "tg.failedLoadStatus": "Не удалось загрузить статус",
  "tg.enterPhone":
    "Введите номер телефона в международном формате, например +15551234567",
  "tg.failedStartLink": "Не удалось начать подключение",
  "tg.enterCode": "Введите код входа из Telegram",
  "tg.invalidCode": "Неверный код",
  "tg.enterPassword": "Введите пароль 2FA",
  "tg.invalidPassword": "Неверный пароль 2FA",
  "tg.accountLinked": "Аккаунт Telegram подключён",
  "tg.accountUnlinked": "Аккаунт Telegram отключён",
  "tg.failedUnlink": "Не удалось отключить",

  // share links
  "share.action": "Поделиться ссылкой",
  "share.title": "Поделиться файлом",
  "share.subtitle":
    "Любой, у кого есть ссылка, сможет просмотреть и скачать этот файл.",
  "share.duration": "Срок действия ссылки",
  "share.create": "Создать ссылку",
  "share.activeLinks": "Активные ссылки",
  "share.noLinks": "Ссылок пока нет.",
  "share.copy": "Копировать ссылку",
  "share.copied": "Ссылка скопирована",
  "share.copyFailed": "Не удалось скопировать ссылку",
  "share.revoke": "Отозвать",
  "share.revoked": "Ссылка отозвана",
  "share.expiresNever": "Бессрочно",
  "share.expiresOn": (p) => `Действует до ${p.date}`,
  "share.expired": "Истекла",
  "share.createFailed": "Не удалось создать ссылку",
  "share.loadFailed": "Не удалось загрузить ссылки",
  "share.revokeFailed": "Не удалось отозвать ссылку",
  "share.dur.1h": "1 час",
  "share.dur.24h": "24 часа",
  "share.dur.7d": "7 дней",
  "share.dur.30d": "30 дней",
  "share.dur.never": "Без срока",
  "sharePage.loading": "Загрузка…",
  "sharePage.download": "Скачать",
  "sharePage.notFoundTitle": "Ссылка недоступна",
  "sharePage.notFoundDesc":
    "Эта ссылка недействительна или срок её действия истёк.",
  "sharePage.sharedVia": "Поделились через Hangar",
  "sharePage.expiresOn": (p) => `Ссылка действует до ${p.date}`,

  // navigation
  "nav.shared": "Доступные мне",

  // internal access grants (user-to-user)
  "grant.action": "Открыть доступ",
  "grant.title": "Открыть доступ",
  "grant.subtitle":
    "Дайте другим пользователям этого Hangar доступ на просмотр.",
  "grant.addPeople": "Добавить пользователей",
  "grant.searchPlaceholder": "Поиск пользователей…",
  "grant.noUsers": "Нет других пользователей для доступа.",
  "grant.noMatches": "Пользователи не найдены.",
  "grant.peopleWithAccess": "У кого есть доступ",
  "grant.noGrants": "Пока ни с кем не поделено.",
  "grant.viewer": "Просмотр",
  "grant.revoke": "Убрать",
  "grant.granted": "Доступ открыт",
  "grant.revoked": "Доступ закрыт",
  "grant.loadFailed": "Не удалось загрузить данные о доступе",
  "grant.grantFailed": "Не удалось открыть доступ",
  "grant.revokeFailed": "Не удалось закрыть доступ",

  // shared-with-me page
  "shared.title": "Доступные мне",
  "shared.subtitle": "Файлы и папки, которыми с вами поделились.",
  "shared.emptyTitle": "Пока с вами ничем не поделились",
  "shared.emptyDesc":
    "Когда кто-то поделится файлом или папкой, это появится здесь.",
  "shared.loadFailed": "Не удалось загрузить общие элементы",
  "shared.sharedBy": (p) => `от ${p.name}`,

  // navigation
  "nav.tags": "Теги",

  // tags
  "tag.action": "Теги",
  "tag.title": "Теги",
  "tag.none": "Тегов пока нет — создайте ниже.",
  "tag.newPlaceholder": "Название тега",
  "tag.create": "Создать",
  "tag.delete": "Удалить тег",
  "tag.untag": "Убрать тег",
  "tag.assignFailed": "Не удалось обновить теги",
  "tag.createFailed": "Не удалось создать тег",
  "tag.deleteFailed": "Не удалось удалить тег",
  "tag.loadFailed": "Не удалось загрузить тег",
  "tag.emptyTitle": "С этим тегом пока ничего нет",
};

export const messages: Record<Locale, Table> = { en, ru: ruTable };
