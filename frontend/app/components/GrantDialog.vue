<script setup lang="ts">
import { UserPlus, Trash2, Loader2, Check, Eye } from 'lucide-vue-next'
import type { Grant, ShareableUser } from '~/types/api'
import { useToast } from '~/composables/useToast'
import { useI18n } from '~/composables/useI18n'
import { Avatar } from '~/components/ui/avatar'
import { Input } from '~/components/ui/input'
import { Button } from '~/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '~/components/ui/dialog'

const props = defineProps<{
  open: boolean
  target: { kind: 'file' | 'folder'; id: string; name: string } | null
}>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const api = useApi()
const { success, error: toastError } = useToast()
const { t } = useI18n()

const grants = ref<Grant[]>([])
const users = ref<ShareableUser[]>([])
const loading = ref(false)
const query = ref('')
const busy = ref<string | null>(null) // recipient id being added/removed

// kind-aware API calls so one dialog serves both files and folders.
function listGrants(id: string) {
  return props.target?.kind === 'folder' ? api.listFolderGrants(id) : api.listFileGrants(id)
}
function createGrant(id: string, recipientId: string) {
  return props.target?.kind === 'folder'
    ? api.createFolderGrant(id, recipientId)
    : api.createFileGrant(id, recipientId)
}
function removeGrant(id: string, recipientId: string) {
  return props.target?.kind === 'folder'
    ? api.deleteFolderGrant(id, recipientId)
    : api.deleteFileGrant(id, recipientId)
}

const grantedIds = computed(() => new Set(grants.value.map((g) => g.recipient_id)))

// Users not yet granted, filtered by the search box.
const candidates = computed(() => {
  const q = query.value.trim().toLowerCase()
  return users.value.filter(
    (u) => !grantedIds.value.has(u.id) && (!q || u.username.toLowerCase().includes(q)),
  )
})

async function load() {
  if (!props.target) return
  loading.value = true
  try {
    const [g, u] = await Promise.all([listGrants(props.target.id), api.listShareableUsers()])
    grants.value = g.grants
    users.value = u.users
  } catch (err) {
    toastError(t('grant.loadFailed'), api.errorMessage(err))
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, props.target?.id] as const,
  ([open]) => {
    if (open) {
      query.value = ''
      load()
    } else {
      grants.value = []
      users.value = []
    }
  },
)

async function add(u: ShareableUser) {
  if (!props.target || busy.value) return
  busy.value = u.id
  try {
    grants.value = (await createGrant(props.target.id, u.id)).grants
    success(t('grant.granted'))
  } catch (err) {
    toastError(t('grant.grantFailed'), api.errorMessage(err))
  } finally {
    busy.value = null
  }
}

async function remove(g: Grant) {
  if (!props.target || busy.value) return
  busy.value = g.recipient_id
  try {
    await removeGrant(props.target.id, g.recipient_id)
    grants.value = grants.value.filter((x) => x.recipient_id !== g.recipient_id)
    success(t('grant.revoked'))
  } catch (err) {
    toastError(t('grant.revokeFailed'), api.errorMessage(err))
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="max-w-md">
      <DialogHeader>
        <DialogTitle>{{ t('grant.title') }}</DialogTitle>
        <DialogDescription>
          <span class="truncate">{{ target?.name }}</span> — {{ t('grant.subtitle') }}
        </DialogDescription>
      </DialogHeader>

      <div v-if="loading" class="flex justify-center py-8">
        <Loader2 class="h-5 w-5 animate-spin text-muted-foreground" />
      </div>

      <template v-else>
        <!-- Add people -->
        <div class="min-w-0">
          <p class="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground/70">
            {{ t('grant.addPeople') }}
          </p>
          <Input v-model="query" :placeholder="t('grant.searchPlaceholder')" />
          <div class="mt-2 max-h-44 space-y-1 overflow-y-auto">
            <p v-if="!users.length" class="py-3 text-center text-sm text-muted-foreground">
              {{ t('grant.noUsers') }}
            </p>
            <p
              v-else-if="!candidates.length"
              class="py-3 text-center text-sm text-muted-foreground"
            >
              {{ t('grant.noMatches') }}
            </p>
            <button
              v-for="u in candidates"
              :key="u.id"
              type="button"
              :disabled="busy === u.id"
              class="flex w-full items-center gap-3 rounded-md px-2 py-1.5 text-left text-sm transition-colors hover:bg-accent/50 disabled:opacity-60"
              @click="add(u)"
            >
              <Avatar :name="u.username" />
              <span class="min-w-0 flex-1 truncate">{{ u.username }}</span>
              <Loader2 v-if="busy === u.id" class="h-4 w-4 shrink-0 animate-spin text-muted-foreground" />
              <UserPlus v-else class="h-4 w-4 shrink-0 text-muted-foreground" />
            </button>
          </div>
        </div>

        <!-- People with access -->
        <div class="min-w-0">
          <p class="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground/70">
            {{ t('grant.peopleWithAccess') }}
          </p>
          <p v-if="!grants.length" class="py-2 text-sm text-muted-foreground">
            {{ t('grant.noGrants') }}
          </p>
          <ul v-else class="space-y-1">
            <li
              v-for="g in grants"
              :key="g.recipient_id"
              class="flex items-center gap-3 rounded-md px-2 py-1.5"
            >
              <Avatar :name="g.recipient_username" />
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium">{{ g.recipient_username }}</p>
                <p class="flex items-center gap-1 text-xs text-muted-foreground">
                  <Eye class="h-3 w-3" /> {{ t('grant.viewer') }}
                </p>
              </div>
              <Button
                variant="ghost"
                size="icon-sm"
                class="shrink-0"
                :aria-label="t('grant.revoke')"
                :disabled="busy === g.recipient_id"
                @click="remove(g)"
              >
                <Loader2 v-if="busy === g.recipient_id" class="h-4 w-4 animate-spin" />
                <Trash2 v-else class="h-4 w-4 text-destructive" />
              </Button>
            </li>
          </ul>
        </div>
      </template>
    </DialogContent>
  </Dialog>
</template>
