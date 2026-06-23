// Shared API types mirroring the backend contract (internal/api JSON views).

export type Role = 'admin' | 'user'

export interface User {
  id: string
  username: string
  role: Role
  created_at: string
}

export interface Folder {
  id: string
  owner_id: string
  parent_id: string | null
  name: string
  created_at: string
}

export interface FileItem {
  id: string
  owner_id: string
  folder_id: string | null
  name: string
  size: number
  mime: string
  sha256: string
  has_thumb: boolean
  created_at: string
}

export interface ListResponse {
  folders: Folder[]
  files: FileItem[]
}

/** One ancestor folder on a search hit's path, ordered root-first. */
export interface PathSegment {
  id: string
  name: string
}

/** A folder search hit: the folder plus the path it lives under. */
export interface FolderHit extends Folder {
  path: PathSegment[]
}

/** A file search hit: the file plus the path it lives under. */
export interface FileHit extends FileItem {
  path: PathSegment[]
}

export interface SearchResponse {
  query: string
  folders: FolderHit[]
  files: FileHit[]
}

export interface Settings {
  generate_thumbnails: boolean
}

/** A public share link for a file (owner-facing). */
export interface Share {
  token: string
  created_at: string
  expires_at: string | null
}

/** A share link the user created, paired with the file it points at (My links). */
export interface MyShare extends Share {
  file: FileItem
}

/** Public metadata for a shared file, served to anyone holding the token. */
export interface SharedFile {
  name: string
  size: number
  mime: string
  has_thumb: boolean
  created_at: string
  expires_at: string | null
}

export interface SetupStatus {
  needs_setup: boolean
}

/** A user that a file/folder can be shared with (id + username only). */
export interface ShareableUser {
  id: string
  username: string
}

/** One internal access grant on a file or folder (owner-facing). */
export interface Grant {
  recipient_id: string
  recipient_username: string
  permission: 'view' | 'edit'
  created_at: string
}

/** A folder shared with the current user, tagged with the sharer's username. */
export interface SharedFolderItem extends Folder {
  owner_username: string
}

/** A file shared with the current user, tagged with the sharer's username. */
export interface SharedFileItem extends FileItem {
  owner_username: string
}

/** Root of "shared with me": items granted directly to the current user. */
export interface SharedRootsResponse {
  folders: SharedFolderItem[]
  files: SharedFileItem[]
}

/** A colour-coded label owned by the user. */
export interface Tag {
  id: string
  name: string
  color: string
  item_count?: number
}

/**
 * Per-user labels bundle: everything the UI needs to render tag badges across
 * listings by item id (loaded once, refreshed on change).
 */
export interface Labels {
  tags: Tag[]
  file_tags: Record<string, string[]>
  folder_tags: Record<string, string[]>
}

export type TelegramStatus = 'not_linked' | 'linking' | 'linked'

export interface TelegramState {
  status: TelegramStatus
  is_premium: boolean
  phone?: string
  awaiting_code?: boolean
  awaiting_password?: boolean
}
