<script setup lang="ts">
import {
  Upload,
  FolderPlus,
  Folder as FolderIco,
  MoreHorizontal,
  Pencil,
  FolderInput,
  Trash2,
  Download,
  Share2,
  UsersRound,
  Tag as TagIcon,
  Inbox,
  ChevronRight,
  Check,
  LayoutGrid,
  List as ListIcon,
  RefreshCw,
} from 'lucide-vue-next'
import type { Folder, FileItem } from '~/types/api'
import { useDriveStore } from '~/stores/drive'
import { useToast } from '~/composables/useToast'
import { useI18n } from '~/composables/useI18n'
import { useLabels } from '~/composables/useLabels'
import { formatSize, formatRelative } from '~/lib/format'
import { isPreviewable } from '~/lib/preview'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Skeleton } from '~/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogFooter,
  DialogTitle,
  DialogDescription,
} from '~/components/ui/dialog'
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogFooter,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogAction,
  AlertDialogCancel,
} from '~/components/ui/alert-dialog'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from '~/components/ui/dropdown-menu'

const drive = useDriveStore()
const api = useApi()
const route = useRoute()
const { error: toastError } = useToast()
const { t } = useI18n()
const labels = useLabels()

onMounted(() => labels.load())

// ---- tags ----
const tagDialogOpen = ref(false)
const tagTarget = ref<{ kind: 'file' | 'folder'; id: string; name: string } | null>(null)

function openTags(kind: 'file' | 'folder', item: Folder | FileItem) {
  tagTarget.value = { kind, id: item.id, name: item.name }
  tagDialogOpen.value = true
}

// ---- context menu (right-click opens a menu at the cursor) ----
const ctx = reactive({
  open: false,
  x: 0,
  y: 0,
  kind: 'file' as 'file' | 'folder',
  item: null as Folder | FileItem | null,
})
const ctxMenuEl = ref<HTMLElement | null>(null)

async function openContext(kind: 'file' | 'folder', item: Folder | FileItem, e: MouseEvent) {
  ctx.open = true
  ctx.kind = kind
  ctx.item = item
  ctx.x = e.clientX
  ctx.y = e.clientY
  // Clamp into the viewport once the menu has a measured size.
  await nextTick()
  const el = ctxMenuEl.value
  if (!el) return
  const r = el.getBoundingClientRect()
  if (ctx.x + r.width > window.innerWidth - 8) ctx.x = Math.max(8, window.innerWidth - r.width - 8)
  if (ctx.y + r.height > window.innerHeight - 8) ctx.y = Math.max(8, window.innerHeight - r.height - 8)
}

function closeContext() {
  ctx.open = false
}

// Shared styling for the cursor menu's items (mirrors DropdownMenuItem).
const ctxItemClass =
  'flex w-full cursor-pointer items-center gap-2 rounded-md px-2.5 py-2 text-left text-sm outline-none transition-colors hover:bg-accent focus:bg-accent'

// Run an action for the right-clicked item, then close the menu.
function runCtx(fn: (item: any) => void) {
  const item = ctx.item
  closeContext()
  if (item) fn(item)
}

// Close on Escape / scroll (positions would otherwise drift).
function onCtxKey(e: KeyboardEvent) {
  if (e.key === 'Escape') closeContext()
}
onMounted(() => {
  window.addEventListener('keydown', onCtxKey)
  window.addEventListener('scroll', closeContext, true)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onCtxKey)
  window.removeEventListener('scroll', closeContext, true)
})

// ---- share ----
const shareOpen = ref(false)
const shareTarget = ref<{ id: string; name: string } | null>(null)

function openShare(f: FileItem) {
  shareTarget.value = { id: f.id, name: f.name }
  shareOpen.value = true
}

// ---- internal access grants (share with users) ----
const grantOpen = ref(false)
const grantTarget = ref<{ kind: 'file' | 'folder'; id: string; name: string } | null>(null)

function openGrant(kind: 'file' | 'folder', item: Folder | FileItem) {
  grantTarget.value = { kind, id: item.id, name: item.name }
  grantOpen.value = true
}

// ---- drag-and-drop move ----
// Drag a file or folder onto another folder in the current view to move it in.
// Drop targets are only the folders currently listed — they are all siblings of
// the dragged item, so a folder can never be dropped into its own descendant and
// no cycle can form. A custom mime marks the drag as internal so it never trips
// the full-window upload overlay (which only reacts to OS "Files").
const DRAG_MIME = 'application/x-hangar-item'
const dragItem = ref<{ kind: 'file' | 'folder'; id: string } | null>(null)
const dragOverFolderId = ref<string | null>(null)

function onItemDragStart(kind: 'file' | 'folder', id: string, e: DragEvent) {
  dragItem.value = { kind, id }
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData(DRAG_MIME, id)
  }
}

function onItemDragEnd() {
  dragItem.value = null
  dragOverFolderId.value = null
}

function canDropOn(folderId: string): boolean {
  const d = dragItem.value
  return !!d && !(d.kind === 'folder' && d.id === folderId)
}

function onFolderDragOver(folderId: string, e: DragEvent) {
  if (!canDropOn(folderId)) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dragOverFolderId.value = folderId
}

function onFolderDragLeave(folderId: string) {
  if (dragOverFolderId.value === folderId) dragOverFolderId.value = null
}

async function onFolderDrop(folder: Folder) {
  const d = dragItem.value
  dragOverFolderId.value = null
  dragItem.value = null
  if (!d || (d.kind === 'folder' && d.id === folder.id)) return
  try {
    if (d.kind === 'file') await drive.moveFile(d.id, folder.id)
    else await drive.moveFolder(d.id, folder.id)
  } catch (err) {
    toastError(t('drive.moveFailed'), api.errorMessage(err))
  }
}

// The URL's `b` param is the source of truth for the current folder, so
// browser back/forward, refresh, and deep links all resolve correctly.
watch(
  () => route.query.b,
  (b) => drive.applyRoute(typeof b === 'string' ? b : null),
  { immediate: true },
)

const isEmpty = computed(() => !drive.folders.length && !drive.files.length)

// Header count summary — only non-zero parts, joined by "·" (no "0 папок").
const countSummary = computed(() => {
  const parts: string[] = []
  if (drive.folders.length) parts.push(t('drive.folderCount', { n: drive.folders.length }))
  if (drive.files.length) parts.push(t('drive.fileCount', { n: drive.files.length }))
  return parts.join(' · ')
})

// ---- view mode (list / grid), persisted ----
const viewMode = ref<'list' | 'grid'>('list')
onMounted(() => {
  const saved = localStorage.getItem('hangar:view')
  if (saved === 'grid' || saved === 'list') viewMode.value = saved
})
watch(viewMode, (v) => localStorage.setItem('hangar:view', v))

// ---- preview ----
const previewOpen = ref(false)
const previewId = ref<string | null>(null)
// Only images are previewable; navigation in the modal stays within them.
const previewFiles = computed(() => drive.files.filter(isPreviewable))
// Thumbnails that failed to load fall back to a type icon.
const thumbFailed = reactive(new Set<string>())

function openFile(f: FileItem) {
  if (isPreviewable(f)) {
    previewId.value = f.id
    previewOpen.value = true
  } else {
    downloadFile(f)
  }
}

function downloadFile(f: FileItem) {
  const a = document.createElement('a')
  a.href = api.downloadUrl(f.id)
  a.download = f.name
  document.body.appendChild(a)
  a.click()
  a.remove()
}

// ---- create folder ----
const createOpen = ref(false)
const createName = ref('')
const creating = ref(false)

function openCreate() {
  createName.value = ''
  createOpen.value = true
}
watch(() => drive.newFolderSignal, openCreate)

async function submitCreate() {
  const name = createName.value.trim()
  if (!name) return
  creating.value = true
  try {
    await drive.createFolder(name)
    createOpen.value = false
  } catch (err) {
    toastError(t('drive.couldNotCreateFolder'), api.errorMessage(err))
  } finally {
    creating.value = false
  }
}

// ---- rename ----
type Target = { kind: 'folder' | 'file'; id: string; name: string }
const renameOpen = ref(false)
const renameTarget = ref<Target | null>(null)
const renameName = ref('')
const renaming = ref(false)

function openRename(kind: 'folder' | 'file', item: Folder | FileItem) {
  renameTarget.value = { kind, id: item.id, name: item.name }
  renameName.value = item.name
  renameOpen.value = true
}

async function submitRename() {
  const t = renameTarget.value
  const name = renameName.value.trim()
  if (!t || !name || name === t.name) {
    renameOpen.value = false
    return
  }
  renaming.value = true
  try {
    if (t.kind === 'folder') await drive.renameFolder({ id: t.id } as Folder, name)
    else await drive.renameFile({ id: t.id } as FileItem, name)
    renameOpen.value = false
  } catch (err) {
    toastError(t('drive.renameFailed'), api.errorMessage(err))
  } finally {
    renaming.value = false
  }
}

// ---- delete ----
const deleteOpen = ref(false)
const deleteTarget = ref<Target | null>(null)
const deleting = ref(false)

function openDelete(kind: 'folder' | 'file', item: Folder | FileItem) {
  deleteTarget.value = { kind, id: item.id, name: item.name }
  deleteOpen.value = true
}

async function confirmDelete() {
  const t = deleteTarget.value
  if (!t) return
  deleting.value = true
  try {
    if (t.kind === 'folder') await drive.deleteFolder({ id: t.id, name: t.name } as Folder)
    else await drive.deleteFile({ id: t.id, name: t.name } as FileItem)
    deleteOpen.value = false
  } catch (err) {
    toastError(t('drive.deleteFailed'), api.errorMessage(err))
  } finally {
    deleting.value = false
  }
}

// ---- move ----
const moveOpen = ref(false)
const moveTarget = ref<Target | null>(null)
const moveChoices = ref<Folder[]>([])
const moveLoading = ref(false)
const moveSelection = ref<string>('root')
const moving = ref(false)

async function openMove(kind: 'folder' | 'file', item: Folder | FileItem) {
  moveTarget.value = { kind, id: item.id, name: item.name }
  moveSelection.value = 'root'
  moveOpen.value = true
  moveLoading.value = true
  try {
    // Backend lists per-parent; offer the top-level folders as destinations.
    const rootList = await api.listFolder(null)
    moveChoices.value = rootList.folders.filter((c) => c.id !== item.id)
  } catch (err) {
    toastError(t('drive.couldNotLoadDestinations'), api.errorMessage(err))
    moveChoices.value = []
  } finally {
    moveLoading.value = false
  }
}

async function confirmMove() {
  const t = moveTarget.value
  if (!t) return
  const dest = moveSelection.value === 'root' ? null : moveSelection.value
  moving.value = true
  try {
    if (t.kind === 'folder') await drive.moveFolder(t.id, dest)
    else await drive.moveFile(t.id, dest)
    moveOpen.value = false
  } catch (err) {
    toastError(t('drive.moveFailed'), api.errorMessage(err))
  } finally {
    moving.value = false
  }
}
</script>

<template>
  <div>
    <!-- Page header -->
    <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-xl font-semibold tracking-tight">
          {{ drive.crumbs[drive.crumbs.length - 1].name }}
        </h1>
        <p v-if="countSummary" class="mt-0.5 text-sm text-muted-foreground">
          {{ countSummary }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <!-- Refresh the current folder listing (e.g. to pick up a file just
             uploaded from another device). -->
        <button
          class="flex h-8 w-8 items-center justify-center rounded-md border text-muted-foreground transition-colors hover:text-foreground disabled:opacity-50"
          :aria-label="t('drive.refresh')"
          :title="t('drive.refresh')"
          :disabled="drive.loading"
          @click="drive.refresh()"
        >
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': drive.loading }" />
        </button>
        <!-- View mode toggle -->
        <div class="mr-1 flex items-center rounded-md border p-0.5">
          <button
            class="flex h-7 w-7 items-center justify-center rounded transition-colors"
            :class="viewMode === 'list' ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground'"
            :aria-label="t('drive.listView')"
            @click="viewMode = 'list'"
          >
            <ListIcon class="h-4 w-4" />
          </button>
          <button
            class="flex h-7 w-7 items-center justify-center rounded transition-colors"
            :class="viewMode === 'grid' ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground'"
            :aria-label="t('drive.gridView')"
            @click="viewMode = 'grid'"
          >
            <LayoutGrid class="h-4 w-4" />
          </button>
        </div>
        <Button variant="outline" @click="openCreate">
          <FolderPlus /> {{ t('drive.newFolder') }}
        </Button>
        <Button @click="drive.requestFilePick()">
          <Upload /> {{ t('drive.upload') }}
        </Button>
      </div>
    </div>

    <p v-if="drive.loadError" class="mb-3 text-sm text-destructive">{{ drive.loadError }}</p>

    <!-- Loading skeletons -->
    <div v-if="drive.loading && isEmpty" class="overflow-hidden rounded-xl border bg-card">
      <div
        v-for="i in 6"
        :key="i"
        class="flex items-center gap-3 border-b px-4 py-3 last:border-0"
      >
        <Skeleton class="h-4 w-4 rounded" />
        <Skeleton class="h-4" :style="{ width: `${30 + (i * 9) % 40}%` }" />
        <Skeleton class="ml-auto h-4 w-16" />
      </div>
    </div>

    <!-- Empty -->
    <EmptyState
      v-else-if="isEmpty"
      :icon="Inbox"
      :title="t('drive.emptyTitle')"
      :description="t('drive.emptyDesc')"
    >
      <div class="grid grid-cols-2 gap-2">
        <Button variant="outline" size="sm" class="w-full" @click="openCreate">
          <FolderPlus /> {{ t('drive.newFolder') }}
        </Button>
        <Button size="sm" class="w-full" @click="drive.requestFilePick()">
          <Upload /> {{ t('drive.uploadFiles') }}
        </Button>
      </div>
    </EmptyState>

    <!-- List view -->
    <div v-else-if="viewMode === 'list'" class="overflow-hidden rounded-xl border bg-card">
      <!-- Column header -->
      <div class="hidden grid-cols-[1fr,7rem,9rem,3rem] items-center gap-3 border-b px-4 py-2.5 text-xs font-medium uppercase tracking-wide text-muted-foreground sm:grid">
        <span>{{ t('drive.colName') }}</span>
        <span>{{ t('drive.colSize') }}</span>
        <span>{{ t('drive.colModified') }}</span>
        <span class="sr-only">{{ t('drive.colActions') }}</span>
      </div>

      <ul class="divide-y">
        <!-- Folders -->
        <li
          v-for="f in drive.folders"
          :key="'fo-' + f.id"
          draggable="true"
          class="group grid grid-cols-[1fr,3rem] items-center gap-3 px-4 py-2.5 transition-colors hover:bg-accent/40 sm:grid-cols-[1fr,7rem,9rem,3rem]"
          :class="dragOverFolderId === f.id ? 'bg-primary/10 ring-1 ring-inset ring-primary' : ''"
          @dragstart="onItemDragStart('folder', f.id, $event)"
          @dragend="onItemDragEnd"
          @dragover="onFolderDragOver(f.id, $event)"
          @dragleave="onFolderDragLeave(f.id)"
          @drop="onFolderDrop(f)"
          @contextmenu.prevent="openContext('folder', f, $event)"
        >
          <div class="flex min-w-0 items-center gap-2">
            <button
              class="flex min-w-0 flex-1 items-center gap-3 text-left"
              @click="drive.openFolder(f)"
            >
              <FolderIco class="h-4 w-4 shrink-0 text-muted-foreground" />
              <span class="truncate text-sm font-medium">{{ f.name }}</span>
            </button>
            <ItemLabels kind="folder" :id="f.id" />
          </div>
          <span class="hidden text-sm text-muted-foreground sm:block">—</span>
          <span class="hidden text-sm text-muted-foreground sm:block">{{ formatRelative(f.created_at) }}</span>
          <div class="flex justify-end">
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button variant="ghost" size="icon-sm" :aria-label="t('drive.folderActions')">
                  <MoreHorizontal />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem @select="drive.openFolder(f)">
                  <FolderIco /> {{ t('drive.open') }}
                </DropdownMenuItem>
                <DropdownMenuItem @select="openTags('folder', f)">
                  <TagIcon /> {{ t('tag.action') }}
                </DropdownMenuItem>
                <DropdownMenuItem @select="openGrant('folder', f)">
                  <UsersRound /> {{ t('grant.action') }}
                </DropdownMenuItem>
                <DropdownMenuItem @select="openRename('folder', f)">
                  <Pencil /> {{ t('drive.rename') }}
                </DropdownMenuItem>
                <DropdownMenuItem @select="openMove('folder', f)">
                  <FolderInput /> {{ t('drive.move') }}
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem variant="destructive" @select="openDelete('folder', f)">
                  <Trash2 /> {{ t('drive.delete') }}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </li>

        <!-- Files -->
        <li
          v-for="f in drive.files"
          :key="'fi-' + f.id"
          draggable="true"
          class="group grid grid-cols-[1fr,3rem] items-center gap-3 px-4 py-2.5 transition-colors hover:bg-accent/40 sm:grid-cols-[1fr,7rem,9rem,3rem]"
          @dragstart="onItemDragStart('file', f.id, $event)"
          @dragend="onItemDragEnd"
          @contextmenu.prevent="openContext('file', f, $event)"
        >
          <div class="flex min-w-0 items-center gap-2">
            <button
              class="flex min-w-0 flex-1 items-center gap-3 text-left"
              @click="openFile(f)"
            >
              <FileIcon :name="f.name" :mime="f.mime" />
              <span class="truncate text-sm">{{ f.name }}</span>
            </button>
            <ItemLabels kind="file" :id="f.id" />
          </div>
          <span class="hidden text-sm text-muted-foreground sm:block">{{ formatSize(f.size) }}</span>
          <span class="hidden text-sm text-muted-foreground sm:block">{{ formatRelative(f.created_at) }}</span>
          <div class="flex justify-end">
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button variant="ghost" size="icon-sm" :aria-label="t('drive.fileActions')">
                  <MoreHorizontal />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem as-child>
                  <a :href="api.downloadUrl(f.id)" :download="f.name">
                    <Download /> {{ t('drive.download') }}
                  </a>
                </DropdownMenuItem>
                <DropdownMenuItem @select="openShare(f)">
                  <Share2 /> {{ t('share.action') }}
                </DropdownMenuItem>
                <DropdownMenuItem @select="openTags('file', f)">
                  <TagIcon /> {{ t('tag.action') }}
                </DropdownMenuItem>
                <DropdownMenuItem @select="openGrant('file', f)">
                  <UsersRound /> {{ t('grant.action') }}
                </DropdownMenuItem>
                <DropdownMenuItem @select="openRename('file', f)">
                  <Pencil /> {{ t('drive.rename') }}
                </DropdownMenuItem>
                <DropdownMenuItem @select="openMove('file', f)">
                  <FolderInput /> {{ t('drive.move') }}
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem variant="destructive" @select="openDelete('file', f)">
                  <Trash2 /> {{ t('drive.delete') }}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </li>
      </ul>
    </div>

    <!-- Grid view -->
    <div
      v-else
      class="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6"
    >
      <!-- Folder tiles -->
      <div
        v-for="f in drive.folders"
        :key="'fo-' + f.id"
        draggable="true"
        class="group relative"
        @dragstart="onItemDragStart('folder', f.id, $event)"
        @dragend="onItemDragEnd"
        @dragover="onFolderDragOver(f.id, $event)"
        @dragleave="onFolderDragLeave(f.id)"
        @drop="onFolderDrop(f)"
        @contextmenu.prevent="openContext('folder', f, $event)"
      >
        <button
          class="flex w-full flex-col gap-2 text-left"
          @click="drive.openFolder(f)"
        >
          <div
            class="flex aspect-square items-center justify-center rounded-xl border bg-card transition-colors group-hover:border-primary/40 group-hover:bg-accent/40"
            :class="dragOverFolderId === f.id ? 'border-primary bg-primary/10 ring-1 ring-inset ring-primary' : ''"
          >
            <FolderIco class="h-10 w-10 text-muted-foreground" />
          </div>
          <span class="truncate px-0.5 text-sm font-medium">{{ f.name }}</span>
        </button>
        <div class="absolute left-2 top-2 flex items-center gap-1 rounded-md bg-background/70 px-1 backdrop-blur-sm">
          <ItemLabels kind="folder" :id="f.id" />
        </div>
        <div class="absolute right-1.5 top-1.5 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100">
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="secondary" size="icon-sm" class="h-7 w-7 shadow-sm" :aria-label="t('drive.folderActions')">
                <MoreHorizontal />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem @select="drive.openFolder(f)"><FolderIco /> {{ t('drive.open') }}</DropdownMenuItem>
              <DropdownMenuItem @select="openTags('folder', f)"><TagIcon /> {{ t('tag.action') }}</DropdownMenuItem>
              <DropdownMenuItem @select="openGrant('folder', f)"><UsersRound /> {{ t('grant.action') }}</DropdownMenuItem>
              <DropdownMenuItem @select="openRename('folder', f)"><Pencil /> {{ t('drive.rename') }}</DropdownMenuItem>
              <DropdownMenuItem @select="openMove('folder', f)"><FolderInput /> {{ t('drive.move') }}</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" @select="openDelete('folder', f)"><Trash2 /> {{ t('drive.delete') }}</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      <!-- File tiles -->
      <div
        v-for="f in drive.files"
        :key="'fi-' + f.id"
        draggable="true"
        class="group relative"
        @dragstart="onItemDragStart('file', f.id, $event)"
        @dragend="onItemDragEnd"
        @contextmenu.prevent="openContext('file', f, $event)"
      >
        <button
          class="flex w-full flex-col gap-2 text-left"
          @click="openFile(f)"
        >
          <div class="flex aspect-square items-center justify-center overflow-hidden rounded-xl border bg-card transition-colors group-hover:border-primary/40">
            <img
              v-if="f.has_thumb && !thumbFailed.has(f.id)"
              :src="api.thumbUrl(f.id)"
              :alt="f.name"
              loading="lazy"
              class="h-full w-full object-cover"
              @error="thumbFailed.add(f.id)"
            >
            <FileIcon v-else :name="f.name" :mime="f.mime" big />
          </div>
          <span class="truncate px-0.5 text-sm">{{ f.name }}</span>
        </button>
        <div class="absolute left-2 top-2 flex items-center gap-1 rounded-md bg-background/70 px-1 backdrop-blur-sm">
          <ItemLabels kind="file" :id="f.id" />
        </div>
        <div class="absolute right-1.5 top-1.5 opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100">
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="secondary" size="icon-sm" class="h-7 w-7 shadow-sm" :aria-label="t('drive.fileActions')">
                <MoreHorizontal />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem as-child>
                <a :href="api.downloadUrl(f.id)" :download="f.name"><Download /> {{ t('drive.download') }}</a>
              </DropdownMenuItem>
              <DropdownMenuItem @select="openShare(f)"><Share2 /> {{ t('share.action') }}</DropdownMenuItem>
              <DropdownMenuItem @select="openTags('file', f)"><TagIcon /> {{ t('tag.action') }}</DropdownMenuItem>
              <DropdownMenuItem @select="openGrant('file', f)"><UsersRound /> {{ t('grant.action') }}</DropdownMenuItem>
              <DropdownMenuItem @select="openRename('file', f)"><Pencil /> {{ t('drive.rename') }}</DropdownMenuItem>
              <DropdownMenuItem @select="openMove('file', f)"><FolderInput /> {{ t('drive.move') }}</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" @select="openDelete('file', f)"><Trash2 /> {{ t('drive.delete') }}</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </div>

    <!-- Create folder dialog -->
    <Dialog v-model:open="createOpen">
      <DialogContent class="max-w-sm">
        <DialogHeader>
          <DialogTitle>{{ t('drive.newFolderTitle') }}</DialogTitle>
          <DialogDescription>{{ t('drive.newFolderDesc', { name: drive.crumbs[drive.crumbs.length - 1].name }) }}</DialogDescription>
        </DialogHeader>
        <div class="grid gap-2">
          <Label for="folder-name">{{ t('common.name') }}</Label>
          <Input
            id="folder-name"
            v-model="createName"
            :placeholder="t('drive.untitledFolder')"
            autofocus
            @keyup.enter="submitCreate"
          />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="createOpen = false">{{ t('common.cancel') }}</Button>
          <Button :disabled="creating || !createName.trim()" @click="submitCreate">
            {{ creating ? t('common.creating') : t('common.create') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Rename dialog -->
    <Dialog v-model:open="renameOpen">
      <DialogContent class="max-w-sm">
        <DialogHeader>
          <DialogTitle>{{ renameTarget?.kind === 'folder' ? t('drive.renameFolderTitle') : t('drive.renameFileTitle') }}</DialogTitle>
        </DialogHeader>
        <div class="grid gap-2">
          <Label for="rename-name">{{ t('common.name') }}</Label>
          <Input id="rename-name" v-model="renameName" autofocus @keyup.enter="submitRename" />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="renameOpen = false">{{ t('common.cancel') }}</Button>
          <Button :disabled="renaming || !renameName.trim()" @click="submitRename">
            {{ renaming ? t('common.saving') : t('common.save') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Move dialog -->
    <Dialog v-model:open="moveOpen">
      <DialogContent class="max-w-sm">
        <DialogHeader>
          <DialogTitle>{{ t('drive.moveTitle', { name: moveTarget?.name ?? '' }) }}</DialogTitle>
          <DialogDescription>{{ t('drive.moveDesc') }}</DialogDescription>
        </DialogHeader>
        <div v-if="moveLoading" class="space-y-2 py-1">
          <Skeleton v-for="i in 3" :key="i" class="h-9 w-full" />
        </div>
        <div v-else class="max-h-64 space-y-1 overflow-y-auto py-1">
          <button
            class="flex w-full items-center gap-3 rounded-md border px-3 py-2 text-left text-sm transition-colors"
            :class="moveSelection === 'root' ? 'border-primary bg-accent' : 'border-transparent hover:bg-accent/50'"
            @click="moveSelection = 'root'"
          >
            <FolderIco class="h-4 w-4 text-muted-foreground" />
            <span class="flex-1">{{ t('drive.moveRoot') }}</span>
            <Check v-if="moveSelection === 'root'" class="h-4 w-4 text-primary" />
          </button>
          <button
            v-for="c in moveChoices"
            :key="c.id"
            class="flex w-full items-center gap-3 rounded-md border px-3 py-2 text-left text-sm transition-colors"
            :class="moveSelection === c.id ? 'border-primary bg-accent' : 'border-transparent hover:bg-accent/50'"
            @click="moveSelection = c.id"
          >
            <FolderIco class="h-4 w-4 text-muted-foreground" />
            <span class="flex-1 truncate">{{ c.name }}</span>
            <Check v-if="moveSelection === c.id" class="h-4 w-4 text-primary" />
          </button>
          <p v-if="!moveChoices.length" class="px-1 py-2 text-xs text-muted-foreground">
            {{ t('drive.noOtherFolders') }}
          </p>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="moveOpen = false">{{ t('common.cancel') }}</Button>
          <Button :disabled="moving || moveLoading" @click="confirmMove">
            {{ moving ? t('drive.moving') : t('drive.moveHere') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Delete confirm -->
    <AlertDialog v-model:open="deleteOpen">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{{ deleteTarget?.kind === 'folder' ? t('drive.deleteFolderTitle') : t('drive.deleteFileTitle') }}</AlertDialogTitle>
          <AlertDialogDescription>
            <template v-if="deleteTarget?.kind === 'folder'">
              {{ t('drive.deleteFolderDesc', { name: deleteTarget?.name ?? '' }) }}
            </template>
            <template v-else>
              {{ t('drive.deleteFileDesc', { name: deleteTarget?.name ?? '' }) }}
            </template>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>{{ t('common.cancel') }}</AlertDialogCancel>
          <AlertDialogAction variant="destructive" :disabled="deleting" @click="confirmDelete">
            {{ deleting ? t('common.deleting') : t('common.delete') }}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>

    <!-- File preview -->
    <FilePreview
      v-model:open="previewOpen"
      v-model:current-id="previewId"
      :files="previewFiles"
    />

    <!-- Public share links -->
    <ShareDialog v-model:open="shareOpen" :file="shareTarget" />

    <!-- Internal access grants -->
    <GrantDialog v-model:open="grantOpen" :target="grantTarget" />

    <!-- Tags -->
    <TagDialog v-model:open="tagDialogOpen" :target="tagTarget" />

    <!-- Right-click context menu (opens at the cursor) -->
    <Teleport to="body">
      <div
        v-if="ctx.open"
        class="fixed inset-0 z-[60]"
        @click="closeContext"
        @contextmenu.prevent="closeContext"
      >
        <div
          ref="ctxMenuEl"
          class="absolute min-w-[12rem] overflow-hidden rounded-lg border bg-popover p-1 text-popover-foreground shadow-lg"
          :style="{ left: ctx.x + 'px', top: ctx.y + 'px' }"
          @click.stop
          @contextmenu.prevent.stop
        >
          <template v-if="ctx.kind === 'folder'">
            <button :class="ctxItemClass" @click="runCtx((f) => drive.openFolder(f))">
              <FolderIco class="h-4 w-4" /> {{ t('drive.open') }}
            </button>
            <button :class="ctxItemClass" @click="runCtx((f) => openTags('folder', f))">
              <TagIcon class="h-4 w-4" /> {{ t('tag.action') }}
            </button>
            <button :class="ctxItemClass" @click="runCtx((f) => openGrant('folder', f))">
              <UsersRound class="h-4 w-4" /> {{ t('grant.action') }}
            </button>
            <div class="my-1 h-px bg-border" />
            <button :class="ctxItemClass" @click="runCtx((f) => openRename('folder', f))">
              <Pencil class="h-4 w-4" /> {{ t('drive.rename') }}
            </button>
            <button :class="ctxItemClass" @click="runCtx((f) => openMove('folder', f))">
              <FolderInput class="h-4 w-4" /> {{ t('drive.move') }}
            </button>
            <div class="my-1 h-px bg-border" />
            <button
              :class="[ctxItemClass, 'text-destructive hover:bg-destructive/10 focus:bg-destructive/10']"
              @click="runCtx((f) => openDelete('folder', f))"
            >
              <Trash2 class="h-4 w-4" /> {{ t('drive.delete') }}
            </button>
          </template>

          <template v-else>
            <button :class="ctxItemClass" @click="runCtx((f) => downloadFile(f))">
              <Download class="h-4 w-4" /> {{ t('drive.download') }}
            </button>
            <button :class="ctxItemClass" @click="runCtx((f) => openShare(f))">
              <Share2 class="h-4 w-4" /> {{ t('share.action') }}
            </button>
            <button :class="ctxItemClass" @click="runCtx((f) => openTags('file', f))">
              <TagIcon class="h-4 w-4" /> {{ t('tag.action') }}
            </button>
            <button :class="ctxItemClass" @click="runCtx((f) => openGrant('file', f))">
              <UsersRound class="h-4 w-4" /> {{ t('grant.action') }}
            </button>
            <div class="my-1 h-px bg-border" />
            <button :class="ctxItemClass" @click="runCtx((f) => openRename('file', f))">
              <Pencil class="h-4 w-4" /> {{ t('drive.rename') }}
            </button>
            <button :class="ctxItemClass" @click="runCtx((f) => openMove('file', f))">
              <FolderInput class="h-4 w-4" /> {{ t('drive.move') }}
            </button>
            <div class="my-1 h-px bg-border" />
            <button
              :class="[ctxItemClass, 'text-destructive hover:bg-destructive/10 focus:bg-destructive/10']"
              @click="runCtx((f) => openDelete('file', f))"
            >
              <Trash2 class="h-4 w-4" /> {{ t('drive.delete') }}
            </button>
          </template>
        </div>
      </div>
    </Teleport>
  </div>
</template>
