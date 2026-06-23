<script setup lang="ts">
import { Link2, Copy, Trash2, Loader2 } from 'lucide-vue-next'
import type { MyShare, FileItem } from '~/types/api'
import { useToast } from '~/composables/useToast'
import { useI18n } from '~/composables/useI18n'
import { formatSize, formatDate } from '~/lib/format'
import { isPreviewable } from '~/lib/preview'
import { Button } from '~/components/ui/button'
import { Skeleton } from '~/components/ui/skeleton'

const api = useApi()
const { success, error: toastError } = useToast()
const { t } = useI18n()

const shares = ref<MyShare[]>([])
const loading = ref(true)
const loadError = ref('')
const revoking = ref<string | null>(null)
const thumbFailed = reactive(new Set<string>())

const isEmpty = computed(() => !shares.value.length)
const previewFiles = computed(() =>
  shares.value.map((s) => s.file).filter(isPreviewable),
)

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    shares.value = (await api.myShares()).shares
  } catch (err) {
    loadError.value = api.errorMessage(err, t('links.loadFailed'))
    shares.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

function linkFor(token: string): string {
  return `${window.location.origin}/s/${token}`
}

function expiryLabel(s: MyShare): string {
  if (!s.expires_at) return t('share.expiresNever')
  if (new Date(s.expires_at).getTime() <= Date.now()) return t('share.expired')
  return t('share.expiresOn', { date: formatDate(s.expires_at) })
}

async function copy(token: string) {
  try {
    await navigator.clipboard.writeText(linkFor(token))
    success(t('share.copied'))
  } catch {
    toastError(t('share.copyFailed'))
  }
}

async function revoke(token: string) {
  revoking.value = token
  try {
    await api.deleteShare(token)
    shares.value = shares.value.filter((s) => s.token !== token)
    success(t('share.revoked'))
  } catch (err) {
    toastError(t('share.revokeFailed'), api.errorMessage(err))
  } finally {
    revoking.value = null
  }
}

// ---- preview / download ----
const previewOpen = ref(false)
const previewId = ref<string | null>(null)

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
    <!-- Header -->
    <div class="mb-5">
      <h1 class="text-xl font-semibold tracking-tight">{{ t('links.title') }}</h1>
      <p class="mt-0.5 text-sm text-muted-foreground">{{ t('links.subtitle') }}</p>
    </div>

    <p v-if="loadError" class="mb-3 text-sm text-destructive">{{ loadError }}</p>

    <!-- Loading -->
    <div v-if="loading && isEmpty" class="overflow-hidden rounded-xl border bg-card">
      <div v-for="i in 5" :key="i" class="flex items-center gap-3 border-b px-4 py-3 last:border-0">
        <Skeleton class="h-6 w-6 rounded" />
        <Skeleton class="h-4" :style="{ width: `${30 + (i * 11) % 40}%` }" />
        <Skeleton class="ml-auto h-4 w-16" />
      </div>
    </div>

    <!-- Empty -->
    <EmptyState
      v-else-if="isEmpty"
      :icon="Link2"
      :title="t('links.emptyTitle')"
      :description="t('links.emptyDesc')"
    />

    <!-- Listing -->
    <div v-else class="overflow-hidden rounded-xl border bg-card">
      <ul class="divide-y">
        <li
          v-for="s in shares"
          :key="s.token"
          class="group flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-accent/40"
        >
          <button class="flex min-w-0 flex-1 items-center gap-3 text-left" @click="openFile(s.file)">
            <img
              v-if="s.file.has_thumb && !thumbFailed.has(s.file.id)"
              :src="api.thumbUrl(s.file.id)"
              :alt="s.file.name"
              loading="lazy"
              class="h-6 w-6 shrink-0 rounded object-cover"
              @error="thumbFailed.add(s.file.id)"
            >
            <FileIcon v-else :name="s.file.name" :mime="s.file.mime" />
            <span class="min-w-0 flex-1">
              <span class="block truncate text-sm">{{ s.file.name }}</span>
              <span class="block truncate font-mono text-xs text-muted-foreground">{{ linkFor(s.token) }}</span>
            </span>
          </button>
          <span class="hidden w-20 shrink-0 text-right text-sm text-muted-foreground sm:block">{{ formatSize(s.file.size) }}</span>
          <span class="hidden w-32 shrink-0 text-right text-xs text-muted-foreground md:block">{{ expiryLabel(s) }}</span>
          <Button variant="ghost" size="icon-sm" class="shrink-0" :aria-label="t('links.copy')" @click="copy(s.token)">
            <Copy class="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            class="shrink-0"
            :aria-label="t('links.revoke')"
            :disabled="revoking === s.token"
            @click="revoke(s.token)"
          >
            <Loader2 v-if="revoking === s.token" class="h-4 w-4 animate-spin" />
            <Trash2 v-else class="h-4 w-4 text-destructive" />
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
