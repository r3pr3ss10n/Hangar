<script setup lang="ts">
import { Folder as FolderIco, ChevronRight, Download, X } from 'lucide-vue-next'
import type { Folder, FileItem } from '~/types/api'
import { useToast } from '~/composables/useToast'
import { useI18n } from '~/composables/useI18n'
import { useLabels } from '~/composables/useLabels'
import { formatSize, formatRelative } from '~/lib/format'
import { isPreviewable } from '~/lib/preview'
import { Button } from '~/components/ui/button'
import { Skeleton } from '~/components/ui/skeleton'

// A read-only folder/file listing used by the Tag page. Opening a folder jumps
// to it in the main drive; files preview (images) or download. When `untagId` is
// set, each row gets a button to remove that tag from the item.
const props = defineProps<{
  folders: Folder[]
  files: FileItem[]
  loading?: boolean
  emptyIcon?: unknown
  emptyTitle: string
  emptyDesc?: string
  untagId?: string
}>()
const emit = defineEmits<{ untag: [{ kind: 'file' | 'folder'; id: string }] }>()

const router = useRouter()
const api = useApi()
const { error: toastError } = useToast()
const { t } = useI18n()
const labels = useLabels()

async function untag(kind: 'file' | 'folder', id: string) {
  if (!props.untagId) return
  try {
    await labels.assignTag(kind, id, props.untagId, false)
    emit('untag', { kind, id })
  } catch (err) {
    toastError(t('tag.assignFailed'), api.errorMessage(err))
  }
}

const isEmpty = computed(() => !props.folders.length && !props.files.length)

function encodePath(trail: { id: string; name: string }[]): string {
  return btoa(unescape(encodeURIComponent(JSON.stringify(trail))))
}

// Open a folder where it actually lives — in the drive, at that folder.
function openFolder(f: Folder) {
  router.push({ path: '/', query: { b: encodePath([{ id: f.id, name: f.name }]) } })
}

// ---- preview / download ----
const previewOpen = ref(false)
const previewId = ref<string | null>(null)
const previewFiles = computed(() => props.files.filter(isPreviewable))
const thumbFailed = reactive(new Set<string>())

function openFile(f: FileItem) {
  if (isPreviewable(f)) {
    previewId.value = f.id
    previewOpen.value = true
  } else {
    const a = document.createElement('a')
    a.href = api.downloadUrl(f.id)
    a.download = f.name
    document.body.appendChild(a)
    a.click()
    a.remove()
  }
}
</script>

<template>
  <div>
    <div v-if="loading && isEmpty" class="overflow-hidden rounded-xl border bg-card">
      <div v-for="i in 5" :key="i" class="flex items-center gap-3 border-b px-4 py-3 last:border-0">
        <Skeleton class="h-4 w-4 rounded" />
        <Skeleton class="h-4" :style="{ width: `${30 + (i * 11) % 40}%` }" />
        <Skeleton class="ml-auto h-4 w-16" />
      </div>
    </div>

    <EmptyState
      v-else-if="isEmpty"
      :icon="emptyIcon"
      :title="emptyTitle"
      :description="emptyDesc"
    />

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
          <ItemLabels kind="folder" :id="f.id" />
          <button
            v-if="untagId"
            type="button"
            class="shrink-0 rounded p-1 text-muted-foreground opacity-0 transition-opacity hover:text-destructive group-hover:opacity-100"
            :aria-label="t('tag.untag')"
            @click="untag('folder', f.id)"
          >
            <X class="h-4 w-4" />
          </button>
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
          <ItemLabels kind="file" :id="f.id" />
          <span class="hidden w-20 shrink-0 text-right text-sm text-muted-foreground sm:block">{{ formatSize(f.size) }}</span>
          <span class="hidden w-28 shrink-0 text-right text-sm text-muted-foreground md:block">{{ formatRelative(f.created_at) }}</span>
          <Button as-child variant="ghost" size="icon-sm" class="shrink-0" :aria-label="t('drive.download')">
            <a :href="api.downloadUrl(f.id)" :download="f.name"><Download class="h-4 w-4" /></a>
          </Button>
          <button
            v-if="untagId"
            type="button"
            class="shrink-0 rounded p-1 text-muted-foreground transition-colors hover:text-destructive"
            :aria-label="t('tag.untag')"
            @click="untag('file', f.id)"
          >
            <X class="h-4 w-4" />
          </button>
        </li>
      </ul>
    </div>

    <FilePreview v-model:open="previewOpen" v-model:current-id="previewId" :files="previewFiles" />
  </div>
</template>
