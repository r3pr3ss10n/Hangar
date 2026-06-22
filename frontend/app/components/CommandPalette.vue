<script setup lang="ts">
import type { Component } from 'vue'
import {
  Search,
  HardDrive,
  Users,
  Send,
  Settings,
  Upload,
  FolderPlus,
  CornerDownLeft,
  Download,
  Folder as FolderIco,
} from 'lucide-vue-next'
import type { FolderHit, FileHit, PathSegment } from '~/types/api'
import { Dialog, DialogContent } from '~/components/ui/dialog'
import { useAuthStore } from '~/stores/auth'
import { useDriveStore } from '~/stores/drive'
import { useCommandPalette } from '~/composables/useCommandPalette'
import { useI18n } from '~/composables/useI18n'

const { open, hide, toggle } = useCommandPalette()
const { t } = useI18n()
const auth = useAuthStore()
const drive = useDriveStore()
const router = useRouter()
const api = useApi()

const query = ref('')
const activeIndex = ref(0)
const inputEl = ref<HTMLInputElement | null>(null)

interface Cmd {
  id: string
  label: string
  hint?: string
  icon: Component
  section: string
  run: () => void
}

const staticCommands = computed<Cmd[]>(() => {
  const cmds: Cmd[] = [
    {
      id: 'nav-drive',
      label: t('cmd.goToDrive'),
      icon: HardDrive,
      section: 'cmd.section.navigation',
      run: () => router.push('/'),
    },
    {
      id: 'nav-settings',
      label: t('cmd.goToSettings'),
      icon: Settings,
      section: 'cmd.section.navigation',
      run: () => router.push('/settings'),
    },
    {
      id: 'act-upload',
      label: t('cmd.uploadFiles'),
      hint: t('cmd.uploadHint'),
      icon: Upload,
      section: 'cmd.section.actions',
      run: () => drive.requestFilePick(),
    },
    {
      id: 'act-newfolder',
      label: t('cmd.newFolder'),
      icon: FolderPlus,
      section: 'cmd.section.actions',
      run: () => {
        router.push('/')
        drive.requestNewFolder()
      },
    },
  ]
  if (auth.isAdmin) {
    cmds.push(
      { id: 'nav-users', label: t('cmd.manageUsers'), icon: Users, section: 'cmd.section.navigation', run: () => router.push('/admin/users') },
      { id: 'nav-tg', label: t('cmd.telegramStorage'), icon: Send, section: 'cmd.section.navigation', run: () => router.push('/admin/telegram') },
    )
  }
  return cmds
})

// `q` (lowercased) filters the static command list; `term` (raw, trimmed) is
// what we send to the backend search.
const q = computed(() => query.value.trim().toLowerCase())
const term = computed(() => query.value.trim())

const filteredCommands = computed(() =>
  q.value
    ? staticCommands.value.filter((c) => c.label.toLowerCase().includes(q.value))
    : staticCommands.value,
)

// ---- drive-wide fuzzy search (backend) ----
const searchFolders = ref<FolderHit[]>([])
const searchFiles = ref<FileHit[]>([])
const searching = ref(false)
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let inFlight: AbortController | null = null
// Monotonic token so a slow response can never overwrite a newer one.
let seq = 0

function resetSearch() {
  searchFolders.value = []
  searchFiles.value = []
  searching.value = false
}

async function runSearch(text: string) {
  const mySeq = ++seq
  inFlight?.abort()
  const controller = new AbortController()
  inFlight = controller
  try {
    const res = await api.search(text, controller.signal)
    if (mySeq !== seq) return
    searchFolders.value = res.folders
    searchFiles.value = res.files
  } catch {
    // Aborted or failed; only surface the empty state if still current.
    if (mySeq === seq) resetSearch()
  } finally {
    if (mySeq === seq) searching.value = false
  }
}

watch(term, (text) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (!text) {
    inFlight?.abort()
    seq++
    resetSearch()
    return
  }
  searching.value = true
  debounceTimer = setTimeout(() => runSearch(text), 160)
})

// Build "My Drive / Reports / Q1" from a hit's root-first ancestor path.
function pathLabel(path: PathSegment[]): string {
  return [t('nav.myDrive'), ...path.map((p) => p.name)].join(' / ')
}

const folderResults = computed<Cmd[]>(() =>
  searchFolders.value.map<Cmd>((f) => ({
    id: 'fo-' + f.id,
    label: f.name,
    hint: pathLabel(f.path),
    icon: FolderIco,
    section: 'cmd.section.folders',
    run: () => drive.revealPath(f.path, { id: f.id, name: f.name }),
  })),
)

const fileResults = computed<Cmd[]>(() =>
  searchFiles.value.map<Cmd>((f) => ({
    id: 'fi-' + f.id,
    label: f.name,
    hint: pathLabel(f.path),
    icon: Download,
    section: 'cmd.section.files',
    run: () => window.open(api.downloadUrl(f.id), '_blank'),
  })),
)

const flat = computed(() => [
  ...filteredCommands.value,
  ...folderResults.value,
  ...fileResults.value,
])

// Group for rendering while keeping a flat index for keyboard nav.
const groups = computed(() => {
  const order = ['cmd.section.navigation', 'cmd.section.actions', 'cmd.section.folders', 'cmd.section.files']
  const map = new Map<string, { cmd: Cmd; index: number }[]>()
  flat.value.forEach((cmd, index) => {
    if (!map.has(cmd.section)) map.set(cmd.section, [])
    map.get(cmd.section)!.push({ cmd, index })
  })
  return order
    .filter((s) => map.has(s))
    .map((s) => ({ name: s, items: map.get(s)! }))
})

watch(q, () => (activeIndex.value = 0))
watch(flat, () => {
  if (activeIndex.value >= flat.value.length) activeIndex.value = Math.max(0, flat.value.length - 1)
})

watch(open, async (isOpen) => {
  if (isOpen) {
    query.value = ''
    activeIndex.value = 0
    resetSearch()
    await nextTick()
    inputEl.value?.focus()
  } else {
    // Drop any in-flight search so a late response can't repopulate a closed
    // palette.
    if (debounceTimer) clearTimeout(debounceTimer)
    inFlight?.abort()
    seq++
  }
})

function runActive() {
  const item = flat.value[activeIndex.value]
  if (!item) return
  hide()
  item.run()
}

function runItem(cmd: Cmd) {
  hide()
  cmd.run()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    if (flat.value.length) activeIndex.value = (activeIndex.value + 1) % flat.value.length
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (flat.value.length)
      activeIndex.value = (activeIndex.value - 1 + flat.value.length) % flat.value.length
  } else if (e.key === 'Enter') {
    e.preventDefault()
    runActive()
  }
}

// Global ⌘K / Ctrl+K shortcut.
function onGlobalKey(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    toggle()
  }
}
onMounted(() => window.addEventListener('keydown', onGlobalKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKey))
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent
      class="top-[12%] max-w-xl translate-y-0 gap-0 overflow-hidden p-0"
      @open-auto-focus.prevent
    >
      <div class="flex items-center gap-2.5 border-b px-4">
        <Search class="h-4 w-4 shrink-0 text-muted-foreground" />
        <input
          ref="inputEl"
          v-model="query"
          :placeholder="t('cmd.searchPlaceholder')"
          class="h-12 w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
          @keydown="onKeydown"
        >
      </div>

      <div class="max-h-[22rem] overflow-y-auto p-2">
        <div
          v-if="searching && !flat.length"
          class="px-3 py-8 text-center text-sm text-muted-foreground"
        >
          {{ t('cmd.searching') }}
        </div>
        <div
          v-else-if="!flat.length"
          class="px-3 py-8 text-center text-sm text-muted-foreground"
        >
          {{ t('cmd.noResults', { q: query }) }}
        </div>

        <div v-for="group in groups" :key="group.name" class="mb-1">
          <div class="px-2 py-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground/70">
            {{ t(group.name) }}
          </div>
          <button
            v-for="{ cmd, index } in group.items"
            :key="cmd.id"
            class="flex w-full items-center gap-3 rounded-md px-2.5 py-2 text-left text-sm transition-colors"
            :class="index === activeIndex ? 'bg-accent text-accent-foreground' : 'hover:bg-accent/50'"
            @click="runItem(cmd)"
            @mousemove="activeIndex = index"
          >
            <component :is="cmd.icon" class="h-4 w-4 shrink-0 text-muted-foreground" />
            <span class="flex-1 truncate">{{ cmd.label }}</span>
            <span
              v-if="cmd.hint"
              class="max-w-[45%] shrink-0 truncate text-xs text-muted-foreground"
            >{{ cmd.hint }}</span>
            <CornerDownLeft
              v-if="index === activeIndex"
              class="h-3.5 w-3.5 shrink-0 text-muted-foreground"
            />
          </button>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
