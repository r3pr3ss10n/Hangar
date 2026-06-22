import { defineStore } from 'pinia'
import type { Folder, FileItem } from '~/types/api'
import { useToast } from '~/composables/useToast'
import { useLabels } from '~/composables/useLabels'
import { t } from '~/composables/useI18n'

export interface Crumb {
  id: string | null
  name: string
}

export interface UploadJob {
  id: number
  name: string
  size: number
  progress: number
  status: 'uploading' | 'done' | 'error' | 'canceled'
  error?: string
}

// AbortControllers live outside reactive state (they must not be proxied),
// keyed by job id so an in-flight upload can be cancelled.
const uploadControllers = new Map<number, AbortController>()

// The breadcrumb trail below the root is encoded into the `b` query param so
// folder navigation lives in the URL: each open/crumb-jump is a real history
// entry (back/forward work), and refreshes / deep links restore the location.
// The backend has no "folder path" endpoint, so we carry the names too.
type PathCrumb = { id: string; name: string }

function encodePath(path: PathCrumb[]): string {
  // unicode-safe base64 of the JSON trail.
  return btoa(unescape(encodeURIComponent(JSON.stringify(path))))
}

function decodePath(b: string | null | undefined): PathCrumb[] {
  if (!b) return []
  try {
    const parsed = JSON.parse(decodeURIComponent(escape(atob(b))))
    if (!Array.isArray(parsed)) return []
    return parsed.filter((c) => c && typeof c.id === 'string' && typeof c.name === 'string')
  } catch {
    return []
  }
}

/**
 * The drive store owns the folder listing, breadcrumb stack, and upload queue.
 * It is shared by the topbar (breadcrumbs + actions), the drive page (listing),
 * and the command palette (navigation + search), so all three stay in sync.
 */
export const useDriveStore = defineStore('drive', {
  state: () => ({
    crumbs: [{ id: null, name: t('nav.myDrive') }] as Crumb[],
    folders: [] as Folder[],
    files: [] as FileItem[],
    loading: false,
    loadError: '',
    uploads: [] as UploadJob[],
    uploadSeq: 0,
    // Bumped to ask the shell to open the OS file picker.
    pickSignal: 0,
    // Bumped to ask the drive page to open its "new folder" dialog.
    newFolderSignal: 0,
  }),

  getters: {
    currentFolderId: (s): string | null => s.crumbs[s.crumbs.length - 1].id,
    activeUploads: (s) => s.uploads.filter((u) => u.status === 'uploading').length,
  },

  actions: {
    async refresh() {
      const api = useApi()
      this.loading = true
      this.loadError = ''
      try {
        const res = await api.listFolder(this.currentFolderId)
        this.folders = res.folders
        this.files = res.files
      } catch (err) {
        this.loadError = api.errorMessage(err, t('store.loadFolderFailed'))
      } finally {
        this.loading = false
      }
    },

    /**
     * Sync breadcrumbs + listing from the URL's `b` param. Called by the drive
     * page on every route change (including back/forward), so the browser
     * history is the single source of truth for the current folder.
     */
    applyRoute(b: string | null | undefined) {
      const trail = decodePath(b)
      this.crumbs = [{ id: null, name: t('nav.myDrive') }, ...trail]
      return this.refresh()
    },

    /** Navigate by pushing the trail into the URL; the route watcher loads it. */
    navigateTo(crumbs: Crumb[]) {
      const router = useRouter()
      const trail = crumbs
        .slice(1)
        .filter((c): c is PathCrumb => typeof c.id === 'string')
      return router.push(trail.length ? { path: '/', query: { b: encodePath(trail) } } : { path: '/' })
    },

    openFolder(f: Folder) {
      return this.navigateTo([...this.crumbs, { id: f.id, name: f.name }])
    },

    /**
     * Navigate to an arbitrary location given its root-first ancestor path
     * (from a search hit). When `into` is provided the trail descends into that
     * folder itself; otherwise it stops at the path's parent (revealing a file's
     * containing folder). Works from any page — it routes to the drive.
     */
    revealPath(path: PathCrumb[], into?: PathCrumb) {
      const crumbs: Crumb[] = [
        { id: null, name: t('nav.myDrive') },
        ...path.map((p) => ({ id: p.id, name: p.name })),
      ]
      if (into) crumbs.push({ id: into.id, name: into.name })
      return this.navigateTo(crumbs)
    },

    goToCrumb(index: number) {
      return this.navigateTo(this.crumbs.slice(0, index + 1))
    },

    async createFolder(name: string) {
      const api = useApi()
      const { toast } = useToast()
      await api.createFolder(name, this.currentFolderId)
      toast(t('store.folderCreated', { name }), { variant: 'success' })
      await this.refresh()
    },

    async renameFolder(f: Folder, name: string) {
      const api = useApi()
      const { toast } = useToast()
      await api.updateFolder(f.id, { name })
      toast(t('store.folderRenamed'), { variant: 'success' })
      await this.refresh()
    },

    async renameFile(f: FileItem, name: string) {
      const api = useApi()
      const { toast } = useToast()
      await api.updateFile(f.id, { name })
      toast(t('store.fileRenamed'), { variant: 'success' })
      await this.refresh()
    },

    async deleteFolder(f: Folder) {
      const api = useApi()
      const { toast } = useToast()
      await api.deleteFolder(f.id)
      toast(t('store.deleted', { name: f.name }), { variant: 'success' })
      await this.refresh()
      // Deleting the folder cascades its tag associations; refresh sidebar counts.
      await useLabels().load(true)
    },

    async deleteFile(f: FileItem) {
      const api = useApi()
      const { toast } = useToast()
      await api.deleteFile(f.id)
      toast(t('store.deleted', { name: f.name }), { variant: 'success' })
      await this.refresh()
      // The deleted file no longer counts toward its tags; refresh sidebar counts.
      await useLabels().load(true)
    },

    async moveFolder(id: string, dest: string | null) {
      const api = useApi()
      const { toast } = useToast()
      await api.updateFolder(id, { parent_id: dest })
      toast(t('store.moved'), { variant: 'success' })
      await this.refresh()
    },

    async moveFile(id: string, dest: string | null) {
      const api = useApi()
      const { toast } = useToast()
      await api.updateFile(id, { folder_id: dest })
      toast(t('store.moved'), { variant: 'success' })
      await this.refresh()
    },

    requestFilePick() {
      this.pickSignal++
    },

    requestNewFolder() {
      this.newFolderSignal++
    },

    patchJob(id: number, patch: Partial<UploadJob>) {
      const live = this.uploads.find((u) => u.id === id)
      if (live) Object.assign(live, patch)
    },

    async startUploads(fileList: FileList | File[]) {
      const api = useApi()
      const { toast } = useToast()
      const arr = Array.from(fileList)
      const folderId = this.currentFolderId
      let failed = 0

      for (const file of arr) {
        const job: UploadJob = {
          id: ++this.uploadSeq,
          name: file.name,
          size: file.size,
          progress: 0,
          status: 'uploading',
        }
        this.uploads.push(job)
        const controller = new AbortController()
        uploadControllers.set(job.id, controller)
        try {
          await api.uploadFile(file, {
            folderId,
            signal: controller.signal,
            onProgress: (fraction) => {
              this.patchJob(job.id, { progress: Math.round(fraction * 100) })
            },
          })
          this.patchJob(job.id, { progress: 100, status: 'done' })
        } catch (err) {
          if (controller.signal.aborted) {
            this.patchJob(job.id, { status: 'canceled' })
          } else {
            this.patchJob(job.id, {
              status: 'error',
              error: api.errorMessage(err, t('store.uploadFailed')),
            })
            failed++
          }
        } finally {
          uploadControllers.delete(job.id)
        }
      }

      // Refresh only if we're still looking at the folder we uploaded into.
      if (folderId === this.currentFolderId) await this.refresh()

      // No success toast: the upload dock already shows per-file completion.
      if (failed) toast(t('store.uploadsFailed', { n: failed }), { variant: 'error' })
    },

    cancelUpload(id: number) {
      uploadControllers.get(id)?.abort()
    },

    clearFinishedUploads() {
      this.uploads = this.uploads.filter((u) => u.status === 'uploading')
    },
  },
})
