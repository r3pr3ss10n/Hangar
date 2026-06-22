<script setup lang="ts">
import {
  Folder as FolderIco,
  ChevronRight,
  Download,
  UsersRound,
} from 'lucide-vue-next'
import type { Folder, FileItem, SharedFolderItem, SharedFileItem } from '~/types/api'
import { useToast } from '~/composables/useToast'
import { useI18n } from '~/composables/useI18n'
import { formatSize, formatRelative } from '~/lib/format'
import { isPreviewable } from '~/lib/preview'
import { Button } from '~/components/ui/button'
import { Skeleton } from '~/components/ui/skeleton'

// The breadcrumb trail (below the shared root) is encoded into ?b=, mirroring the
// drive page, so back/forward and deep links work. The shared root is virtual
// (an aggregation of everything shared with me), so an empty trail = root.
type Crumb = { id: string; name: string }

function encodePath(path: Crumb[]): string {
  return btoa(unescape(encodeURIComponent(JSON.stringify(path))))
}
function decodePath(b: string | null | undefined): Crumb[] {
  if (!b) return []
  try {
    const parsed = JSON.parse(decodeURIComponent(escape(atob(b))))
    if (!Array.isArray(parsed)) return []
    return parsed.filter((c) => c && typeof c.id === 'string' && typeof c.name === 'string')
  } catch {
    return []
  }
}

const route = useRoute()
const router = useRouter()
const api = useApi()
const { error: toastError } = useToast()
const { t } = useI18n()

const crumbs = ref<Crumb[]>([])
// Folders/files carry an optional owner_username (present only at the root).
const folders = ref<(Folder & { owner_username?: string })[]>([])
const files = ref<(FileItem & { owner_username?: string })[]>([])
const loading = ref(true)
const loadError = ref('')

const atRoot = computed(() => crumbs.value.length === 0)
const isEmpty = computed(() => !folders.value.length && !files.value.length)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    if (crumbs.value.length === 0) {
      const res = await api.sharedRoots()
      folders.value = res.folders
      files.value = res.files
    } else {
      const currentId = crumbs.value[crumbs.value.length - 1].id
      const res = await api.sharedChildren(currentId)
      folders.value = res.folders
      files.value = res.files
    }
  } catch (err) {
    loadError.value = api.errorMessage(err, t('shared.loadFailed'))
    folders.value = []
    files.value = []
  } finally {
    loading.value = false
  }
}

// The URL's `b` param is the source of truth for the current location.
watch(
  () => route.query.b,
  (b) => {
    crumbs.value = decodePath(typeof b === 'string' ? b : null)
    load()
  },
  { immediate: true },
)

function openFolder(f: Folder) {
  const trail = [...crumbs.value, { id: f.id, name: f.name }]
  router.push({ path: '/shared', query: { b: encodePath(trail) } })
}

function goToRoot() {
  router.push({ path: '/shared' })
}

function goToCrumb(index: number) {
  const trail = crumbs.value.slice(0, index + 1)
  router.push({ path: '/shared', query: { b: encodePath(trail) } })
}

// ---- preview / download ----
const previewOpen = ref(false)
const previewId = ref<string | null>(null)
const previewFiles = computed(() => files.value.filter(isPreviewable))
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
</script>

<template>
  <div>
    <!-- Header -->
    <div class="mb-5">
      <h1 class="text-xl font-semibold tracking-tight">{{ t('shared.title') }}</h1>
      <p class="mt-0.5 text-sm text-muted-foreground">{{ t('shared.subtitle') }}</p>
    </div>

    <!-- Breadcrumbs (only once inside a shared folder) -->
    <nav v-if="!atRoot" class="mb-3 flex items-center gap-0.5 overflow-x-auto text-sm">
      <button
        class="shrink-0 rounded-md px-2 py-1 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
        @click="goToRoot"
      >
        {{ t('shared.title') }}
      </button>
      <template v-for="(c, i) in crumbs" :key="c.id">
        <ChevronRight class="h-3.5 w-3.5 shrink-0 text-muted-foreground/50" />
        <button
          class="shrink-0 rounded-md px-2 py-1 transition-colors hover:bg-accent"
          :class="i === crumbs.length - 1 ? 'font-medium text-foreground' : 'text-muted-foreground hover:text-foreground'"
          @click="goToCrumb(i)"
        >
          {{ c.name }}
        </button>
      </template>
    </nav>

    <p v-if="loadError" class="mb-3 text-sm text-destructive">{{ loadError }}</p>

    <!-- Loading -->
    <div v-if="loading && isEmpty" class="overflow-hidden rounded-xl border bg-card">
      <div v-for="i in 5" :key="i" class="flex items-center gap-3 border-b px-4 py-3 last:border-0">
        <Skeleton class="h-4 w-4 rounded" />
        <Skeleton class="h-4" :style="{ width: `${30 + (i * 11) % 40}%` }" />
        <Skeleton class="ml-auto h-4 w-16" />
      </div>
    </div>

    <!-- Empty -->
    <EmptyState
      v-else-if="isEmpty"
      :icon="UsersRound"
      :title="t('shared.emptyTitle')"
      :description="t('shared.emptyDesc')"
    />

    <!-- Listing (read-only) -->
    <div v-else class="overflow-hidden rounded-xl border bg-card">
      <ul class="divide-y">
        <!-- Folders -->
        <li
          v-for="f in folders"
          :key="'fo-' + f.id"
          class="group flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-accent/40"
        >
          <button class="flex min-w-0 flex-1 items-center gap-3 text-left" @click="openFolder(f)">
            <FolderIco class="h-4 w-4 shrink-0 text-muted-foreground" />
            <span class="truncate text-sm font-medium">{{ f.name }}</span>
            <ChevronRight class="h-3.5 w-3.5 shrink-0 text-muted-foreground/0 transition-colors group-hover:text-muted-foreground/60" />
          </button>
          <span v-if="f.owner_username" class="shrink-0 text-xs text-muted-foreground">
            {{ t('shared.sharedBy', { name: f.owner_username }) }}
          </span>
        </li>

        <!-- Files -->
        <li
          v-for="f in files"
          :key="'fi-' + f.id"
          class="group flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-accent/40"
        >
          <button class="flex min-w-0 flex-1 items-center gap-3 text-left" @click="openFile(f)">
            <img
              v-if="f.has_thumb && !thumbFailed.has(f.id)"
              :src="api.thumbUrl(f.id)"
              :alt="f.name"
              loading="lazy"
              class="h-6 w-6 shrink-0 rounded object-cover"
              @error="thumbFailed.add(f.id)"
            >
            <FileIcon v-else :name="f.name" :mime="f.mime" />
            <span class="truncate text-sm">{{ f.name }}</span>
          </button>
          <span v-if="f.owner_username" class="hidden shrink-0 text-xs text-muted-foreground sm:inline">
            {{ t('shared.sharedBy', { name: f.owner_username }) }}
          </span>
          <span class="hidden w-20 shrink-0 text-right text-sm text-muted-foreground sm:block">{{ formatSize(f.size) }}</span>
          <span class="hidden w-28 shrink-0 text-right text-sm text-muted-foreground md:block">{{ formatRelative(f.created_at) }}</span>
          <Button
            as-child
            variant="ghost"
            size="icon-sm"
            class="shrink-0"
            :aria-label="t('drive.download')"
          >
            <a :href="api.downloadUrl(f.id)" :download="f.name"><Download class="h-4 w-4" /></a>
          </Button>
        </li>
      </ul>
    </div>

    <!-- Image preview -->
    <FilePreview
      v-model:open="previewOpen"
      v-model:current-id="previewId"
      :files="previewFiles"
    />
  </div>
</template>
