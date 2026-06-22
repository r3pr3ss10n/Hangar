import type {
  User,
  Folder,
  FileItem,
  ListResponse,
  SearchResponse,
  Settings,
  SetupStatus,
  TelegramState,
  Share,
  SharedFile,
  ShareableUser,
  Grant,
  SharedRootsResponse,
  Tag,
  Labels,
  Role,
} from '~/types/api'

/**
 * useApi returns a typed client over the Hangar backend. Every call sends the
 * session cookie (credentials: "include") and is rooted at the configured
 * apiBase. Errors surface as the thrown $fetch error; callers may read
 * err.data.error for the backend message.
 */
export function useApi() {
  const config = useRuntimeConfig()
  const baseURL = config.public.apiBase as string

  const api = $fetch.create({
    baseURL,
    credentials: 'include',
  })

  // ---- helpers ----

  /** Extracts the {error} message from a thrown $fetch error, if present. */
  function errorMessage(err: unknown, fallback = 'Request failed'): string {
    const e = err as { data?: { error?: string }; message?: string }
    return e?.data?.error || e?.message || fallback
  }

  // ---- setup / auth ----

  const setupStatus = () => api<SetupStatus>('/api/setup/status')

  const setup = (username: string, password: string) =>
    api<{ user: User }>('/api/setup', {
      method: 'POST',
      body: { username, password },
    })

  const login = (username: string, password: string) =>
    api<{ user: User }>('/api/auth/login', {
      method: 'POST',
      body: { username, password },
    })

  const logout = () =>
    api('/api/auth/logout', { method: 'POST' })

  const me = () => api<{ user: User }>('/api/auth/me')

  /** Bytes of (non-deleted) files the current user owns. Informational only. */
  const storage = () => api<{ used_bytes: number }>('/api/storage')

  // ---- settings ----

  const getSettings = () => api<Settings>('/api/settings')

  const updateSettings = (patch: Partial<Settings>) =>
    api<Settings>('/api/settings', { method: 'PATCH', body: patch })

  // ---- folders ----

  const listFolder = (parentId?: string | null) =>
    api<ListResponse>('/api/folders', {
      query: parentId ? { parent_id: parentId } : { parent_id: 'root' },
    })

  const createFolder = (name: string, parentId?: string | null) =>
    api<{ folder: Folder }>('/api/folders', {
      method: 'POST',
      body: { name, parent_id: parentId ?? null },
    })

  const updateFolder = (
    id: string,
    patch: { name?: string; parent_id?: string | null },
  ) =>
    api(`/api/folders/${id}`, { method: 'PATCH', body: patch })

  const deleteFolder = (id: string) =>
    api(`/api/folders/${id}`, { method: 'DELETE' })

  // ---- search ----

  /**
   * search runs a fuzzy, drive-wide search over the owner's folder and file
   * names. Each hit carries the folder path it lives under (root-first) so the
   * UI can show its location and navigate straight to it.
   */
  const search = (q: string, signal?: AbortSignal) =>
    api<SearchResponse>('/api/search', { query: { q }, signal })

  // ---- files (metadata) ----

  const fileMeta = (id: string) =>
    api<{ file: FileItem }>(`/api/files/${id}/meta`)

  const updateFile = (
    id: string,
    patch: { name?: string; folder_id?: string | null },
  ) =>
    api(`/api/files/${id}`, { method: 'PATCH', body: patch })

  const deleteFile = (id: string) =>
    api(`/api/files/${id}`, { method: 'DELETE' })

  /** Absolute URL for a direct (Range-capable) browser download. */
  const downloadUrl = (id: string) => `${baseURL}/api/files/${id}`

  /** Absolute URL for a file's JPEG thumbnail (404 if it has none). */
  const thumbUrl = (id: string) => `${baseURL}/api/files/${id}/thumb`

  // ---- share links ----

  const listShares = (fileId: string) =>
    api<{ shares: Share[] }>(`/api/files/${fileId}/shares`)

  /**
   * createShare mints a public link for a file. `expiresInSeconds` is the
   * lifetime from now; pass null for a link that never expires.
   */
  const createShare = (fileId: string, expiresInSeconds: number | null) =>
    api<Share>(`/api/files/${fileId}/shares`, {
      method: 'POST',
      body: { expires_in_seconds: expiresInSeconds },
    })

  const deleteShare = (token: string) =>
    api(`/api/shares/${token}`, { method: 'DELETE' })

  /** Public metadata for a shared file (no auth needed). */
  const shareInfo = (token: string) =>
    api<SharedFile>(`/api/share/${token}`)

  /** Absolute URL anyone can use to download a shared file. */
  const shareDownloadUrl = (token: string) => `${baseURL}/api/share/${token}/download`

  /** Absolute URL for a shared file's thumbnail (404 if it has none). */
  const shareThumbUrl = (token: string) => `${baseURL}/api/share/${token}/thumb`

  // ---- internal access grants (user-to-user sharing) ----

  const listShareableUsers = () =>
    api<{ users: ShareableUser[] }>('/api/users/shareable')

  const listFileGrants = (fileId: string) =>
    api<{ grants: Grant[] }>(`/api/files/${fileId}/grants`)

  const createFileGrant = (fileId: string, recipientId: string) =>
    api<{ grants: Grant[] }>(`/api/files/${fileId}/grants`, {
      method: 'POST',
      body: { recipient_id: recipientId, permission: 'view' },
    })

  const deleteFileGrant = (fileId: string, recipientId: string) =>
    api(`/api/files/${fileId}/grants/${recipientId}`, { method: 'DELETE' })

  const listFolderGrants = (folderId: string) =>
    api<{ grants: Grant[] }>(`/api/folders/${folderId}/grants`)

  const createFolderGrant = (folderId: string, recipientId: string) =>
    api<{ grants: Grant[] }>(`/api/folders/${folderId}/grants`, {
      method: 'POST',
      body: { recipient_id: recipientId, permission: 'view' },
    })

  const deleteFolderGrant = (folderId: string, recipientId: string) =>
    api(`/api/folders/${folderId}/grants/${recipientId}`, { method: 'DELETE' })

  // ---- "shared with me" browsing ----

  /** Roots shared directly with the current user. */
  const sharedRoots = () => api<SharedRootsResponse>('/api/shared')

  /** Children of a folder the current user can access via a grant. */
  const sharedChildren = (folderId: string) =>
    api<ListResponse>(`/api/shared/folders/${folderId}`)

  // ---- labels: favourites + tags ----

  /** The per-user labels bundle (tag list + assignments). */
  const getLabels = () => api<Labels>('/api/labels')

  const listTags = () => api<{ tags: Tag[] }>('/api/tags')

  const createTag = (name: string, color: string) =>
    api<Tag>('/api/tags', { method: 'POST', body: { name, color } })

  const updateTag = (id: string, name: string, color: string) =>
    api(`/api/tags/${id}`, { method: 'PATCH', body: { name, color } })

  const deleteTag = (id: string) => api(`/api/tags/${id}`, { method: 'DELETE' })

  const tagItems = (id: string) => api<ListResponse>(`/api/tags/${id}/items`)

  const addFileTag = (id: string, tagId: string) =>
    api(`/api/files/${id}/tags`, { method: 'POST', body: { tag_id: tagId } })

  const removeFileTag = (id: string, tagId: string) =>
    api(`/api/files/${id}/tags/${tagId}`, { method: 'DELETE' })

  const addFolderTag = (id: string, tagId: string) =>
    api(`/api/folders/${id}/tags`, { method: 'POST', body: { tag_id: tagId } })

  const removeFolderTag = (id: string, tagId: string) =>
    api(`/api/folders/${id}/tags/${tagId}`, { method: 'DELETE' })

  // ---- users (admin) ----

  const listUsers = () => api<{ users: User[] }>('/api/users')

  const createUser = (username: string, password: string, role: Role) =>
    api<{ user: User }>('/api/users', {
      method: 'POST',
      body: { username, password, role },
    })

  const deleteUser = (id: string) =>
    api(`/api/users/${id}`, { method: 'DELETE' })

  const setUserPassword = (id: string, password: string) =>
    api(`/api/users/${id}/password`, {
      method: 'POST',
      body: { password },
    })

  // ---- telegram (admin) ----

  const telegramStatus = () => api<TelegramState>('/api/telegram/status')

  const telegramLinkStart = (phone: string) =>
    api<{ link_id: string }>('/api/telegram/link/start', {
      method: 'POST',
      body: { phone },
    })

  const telegramLinkCode = (linkId: string, code: string) =>
    api<{ need_password: boolean }>('/api/telegram/link/code', {
      method: 'POST',
      body: { link_id: linkId, code },
    })

  const telegramLinkPassword = (linkId: string, password: string) =>
    api<{ status: string }>('/api/telegram/link/password', {
      method: 'POST',
      body: { link_id: linkId, password },
    })

  const telegramLinkCancel = (linkId: string) =>
    api('/api/telegram/link/cancel', {
      method: 'POST',
      body: { link_id: linkId },
    })

  const telegramUnlink = () =>
    api('/api/telegram/unlink', { method: 'POST' })

  // ---- streaming upload with progress (XHR) ----

  /**
   * uploadFile streams a single file into the backend via the streaming
   * POST /api/files path, reporting progress through onProgress (0..1). It
   * sends X-Upload-Filename / Content-Type / Content-Length as required by the
   * contract, with the cookie credentials enabled. Resolves to the created
   * file row on 201.
   */
  function uploadFile(
    file: File,
    opts: {
      folderId?: string | null
      onProgress?: (fraction: number) => void
      signal?: AbortSignal
    } = {},
  ): Promise<FileItem> {
    return new Promise<FileItem>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      const query = opts.folderId ? `?folder_id=${encodeURIComponent(opts.folderId)}` : ''
      xhr.open('POST', `${baseURL}/api/files${query}`, true)
      xhr.withCredentials = true
      xhr.setRequestHeader('X-Upload-Filename', encodeURIComponent(file.name))
      xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream')
      // Content-Length is set by the browser from the body; we cannot override
      // forbidden headers via setRequestHeader, so the body's byte length is
      // authoritative. The backend reads it from the request.

      if (xhr.upload && opts.onProgress) {
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) opts.onProgress!(e.loaded / e.total)
        }
      }

      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          try {
            const body = JSON.parse(xhr.responseText)
            resolve(body.file as FileItem)
          } catch (err) {
            reject(new Error('Upload succeeded but response was unreadable'))
          }
        } else {
          let msg = `Upload failed (${xhr.status})`
          try {
            const body = JSON.parse(xhr.responseText)
            if (body?.error) msg = body.error
          } catch {
            // ignore parse failure, keep status message
          }
          reject(new Error(msg))
        }
      }

      xhr.onerror = () => reject(new Error('Network error during upload'))
      xhr.onabort = () => reject(new Error('Upload aborted'))

      if (opts.signal) {
        if (opts.signal.aborted) {
          xhr.abort()
          return
        }
        opts.signal.addEventListener('abort', () => xhr.abort())
      }

      xhr.send(file)
    })
  }

  return {
    errorMessage,
    setupStatus,
    setup,
    login,
    logout,
    me,
    storage,
    getSettings,
    updateSettings,
    search,
    listFolder,
    createFolder,
    updateFolder,
    deleteFolder,
    fileMeta,
    updateFile,
    deleteFile,
    downloadUrl,
    thumbUrl,
    listShares,
    createShare,
    deleteShare,
    shareInfo,
    shareDownloadUrl,
    shareThumbUrl,
    listShareableUsers,
    listFileGrants,
    createFileGrant,
    deleteFileGrant,
    listFolderGrants,
    createFolderGrant,
    deleteFolderGrant,
    sharedRoots,
    sharedChildren,
    getLabels,
    listTags,
    createTag,
    updateTag,
    deleteTag,
    tagItems,
    addFileTag,
    removeFileTag,
    addFolderTag,
    removeFolderTag,
    listUsers,
    createUser,
    deleteUser,
    setUserPassword,
    telegramStatus,
    telegramLinkStart,
    telegramLinkCode,
    telegramLinkPassword,
    telegramLinkCancel,
    telegramUnlink,
    uploadFile,
  }
}
