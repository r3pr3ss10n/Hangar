<script setup lang="ts">
import { Link2, Copy, Trash2, Loader2, ChevronsUpDown, Check, Clock } from 'lucide-vue-next'
import type { Share } from '~/types/api'
import { useToast } from '~/composables/useToast'
import { useI18n } from '~/composables/useI18n'
import { formatDate } from '~/lib/format'
import { Button } from '~/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '~/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from '~/components/ui/dropdown-menu'

const props = defineProps<{
  open: boolean
  file: { id: string; name: string } | null
}>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const api = useApi()
const { success, error: toastError } = useToast()
const { t } = useI18n()

// Expiry presets (seconds; null = never). Default to a week.
const durations: { key: string; seconds: number | null }[] = [
  { key: 'share.dur.1h', seconds: 3600 },
  { key: 'share.dur.24h', seconds: 86400 },
  { key: 'share.dur.7d', seconds: 604800 },
  { key: 'share.dur.30d', seconds: 2592000 },
  { key: 'share.dur.never', seconds: null },
]
const selected = ref(durations[2])

const shares = ref<Share[]>([])
const loading = ref(false)
const creating = ref(false)
const revoking = ref<string | null>(null)

function linkFor(token: string): string {
  return `${window.location.origin}/s/${token}`
}

function expiryLabel(s: Share): string {
  if (!s.expires_at) return t('share.expiresNever')
  if (new Date(s.expires_at).getTime() <= Date.now()) return t('share.expired')
  return t('share.expiresOn', { date: formatDate(s.expires_at) })
}

async function load() {
  if (!props.file) return
  loading.value = true
  try {
    shares.value = (await api.listShares(props.file.id)).shares
  } catch (err) {
    toastError(t('share.loadFailed'), api.errorMessage(err))
  } finally {
    loading.value = false
  }
}

// Load the file's links each time the dialog opens for a (new) file.
watch(
  () => [props.open, props.file?.id] as const,
  ([open]) => {
    if (open) {
      selected.value = durations[2]
      load()
    } else {
      shares.value = []
    }
  },
)

async function create() {
  if (!props.file || creating.value) return
  creating.value = true
  try {
    const share = await api.createShare(props.file.id, selected.value.seconds)
    shares.value = [share, ...shares.value]
    await copy(share.token)
  } catch (err) {
    toastError(t('share.createFailed'), api.errorMessage(err))
  } finally {
    creating.value = false
  }
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
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="max-w-md">
      <DialogHeader>
        <DialogTitle>{{ t('share.title') }}</DialogTitle>
        <DialogDescription>
          <span class="truncate">{{ file?.name }}</span> — {{ t('share.subtitle') }}
        </DialogDescription>
      </DialogHeader>

      <!-- Create a new link -->
      <div class="flex min-w-0 items-end gap-2">
        <div class="grid min-w-0 flex-1 gap-1.5">
          <label class="text-xs font-medium text-muted-foreground">{{ t('share.duration') }}</label>
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <button
                type="button"
                class="flex h-9 w-full items-center gap-2 rounded-md border bg-background px-3 text-sm outline-none ring-ring ring-offset-2 ring-offset-background transition-colors hover:bg-accent/50 focus-visible:ring-2"
              >
                <Clock class="h-4 w-4 text-muted-foreground" />
                <span class="flex-1 text-left">{{ t(selected.key) }}</span>
                <ChevronsUpDown class="h-4 w-4 text-muted-foreground" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" class="min-w-[12rem]">
              <DropdownMenuItem
                v-for="d in durations"
                :key="d.key"
                @select="selected = d"
              >
                <span class="flex-1">{{ t(d.key) }}</span>
                <Check v-if="selected.key === d.key" class="h-4 w-4 text-primary" />
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
        <Button class="shrink-0" :disabled="creating" @click="create">
          <Loader2 v-if="creating" class="animate-spin" />
          <Link2 v-else />
          {{ t('share.create') }}
        </Button>
      </div>

      <!-- Existing links -->
      <div class="mt-1 min-w-0">
        <p class="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground/70">
          {{ t('share.activeLinks') }}
        </p>

        <div v-if="loading" class="flex justify-center py-6">
          <Loader2 class="h-5 w-5 animate-spin text-muted-foreground" />
        </div>

        <p v-else-if="!shares.length" class="py-4 text-center text-sm text-muted-foreground">
          {{ t('share.noLinks') }}
        </p>

        <ul v-else class="space-y-2">
          <li
            v-for="s in shares"
            :key="s.token"
            class="flex min-w-0 items-center gap-1 rounded-lg border p-2.5"
          >
            <div class="min-w-0 flex-1">
              <p class="truncate font-mono text-xs">{{ linkFor(s.token) }}</p>
              <p class="mt-0.5 text-xs text-muted-foreground">{{ expiryLabel(s) }}</p>
            </div>
            <Button variant="ghost" size="icon-sm" class="shrink-0" :aria-label="t('share.copy')" @click="copy(s.token)">
              <Copy class="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              class="shrink-0"
              :aria-label="t('share.revoke')"
              :disabled="revoking === s.token"
              @click="revoke(s.token)"
            >
              <Loader2 v-if="revoking === s.token" class="h-4 w-4 animate-spin" />
              <Trash2 v-else class="h-4 w-4 text-destructive" />
            </Button>
          </li>
        </ul>
      </div>
    </DialogContent>
  </Dialog>
</template>
