<script setup lang="ts">
import { Download, ChevronLeft, ChevronRight, ImageOff } from 'lucide-vue-next'
import type { FileItem } from '~/types/api'
import { formatSize, formatRelative } from '~/lib/format'
import { useI18n } from '~/composables/useI18n'
import { Dialog, DialogContent } from '~/components/ui/dialog'
import { Button } from '~/components/ui/button'

// `files` is the list of previewable (image) files; navigation stays within it.
const props = defineProps<{
  open: boolean
  files: FileItem[]
  currentId: string | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'update:currentId': [value: string | null]
}>()

const api = useApi()
const { t } = useI18n()

const current = computed(() => props.files.find((f) => f.id === props.currentId) ?? null)
const index = computed(() => props.files.findIndex((f) => f.id === props.currentId))

const hasPrev = computed(() => index.value > 0)
const hasNext = computed(() => index.value >= 0 && index.value < props.files.length - 1)

// Track images that fail to load so we can show a graceful fallback.
const failed = reactive(new Set<string>())

function go(delta: number) {
  const next = props.files[index.value + delta]
  if (next) emit('update:currentId', next.id)
}

function onKey(e: KeyboardEvent) {
  if (!props.open) return
  if (e.key === 'ArrowLeft' && hasPrev.value) {
    e.preventDefault()
    go(-1)
  } else if (e.key === 'ArrowRight' && hasNext.value) {
    e.preventDefault()
    go(1)
  }
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent
      class="flex max-h-[90vh] w-[min(64rem,95vw)] max-w-none flex-col gap-0 overflow-hidden p-0"
    >
      <!-- Header -->
      <div v-if="current" class="flex min-w-0 items-center gap-3 border-b px-4 py-3">
        <FileIcon :name="current.name" :mime="current.mime" />
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-medium">{{ current.name }}</p>
          <p class="text-xs text-muted-foreground">
            {{ formatSize(current.size) }} · {{ formatRelative(current.created_at) }}
          </p>
        </div>
        <span v-if="files.length > 1" class="shrink-0 text-xs tabular-nums text-muted-foreground">
          {{ index + 1 }} / {{ files.length }}
        </span>
        <Button as-child variant="outline" size="sm">
          <a :href="api.downloadUrl(current.id)" :download="current.name">
            <Download /> {{ t('preview.download') }}
          </a>
        </Button>
      </div>

      <!-- Image -->
      <div class="relative flex min-h-0 flex-1 items-center justify-center overflow-auto bg-muted/30">
        <button
          v-if="hasPrev"
          class="absolute left-2 top-1/2 z-10 -translate-y-1/2 rounded-full bg-background/80 p-2 shadow-sm backdrop-blur transition-colors hover:bg-background"
          :aria-label="t('preview.previous')"
          @click="go(-1)"
        >
          <ChevronLeft class="h-5 w-5" />
        </button>
        <button
          v-if="hasNext"
          class="absolute right-2 top-1/2 z-10 -translate-y-1/2 rounded-full bg-background/80 p-2 shadow-sm backdrop-blur transition-colors hover:bg-background"
          :aria-label="t('preview.next')"
          @click="go(1)"
        >
          <ChevronRight class="h-5 w-5" />
        </button>

        <template v-if="current">
          <img
            v-if="!failed.has(current.id)"
            :src="api.downloadUrl(current.id)"
            :alt="current.name"
            class="max-h-[78vh] w-auto object-contain"
            @error="failed.add(current.id)"
          >
          <div v-else class="flex flex-col items-center gap-3 p-12 text-center">
            <ImageOff class="h-12 w-12 text-muted-foreground/50" />
            <p class="text-sm text-muted-foreground">{{ t('preview.couldNotLoad') }}</p>
            <Button as-child size="sm">
              <a :href="api.downloadUrl(current.id)" :download="current.name">
                <Download /> {{ t('preview.download') }}
              </a>
            </Button>
          </div>
        </template>
      </div>
    </DialogContent>
  </Dialog>
</template>
