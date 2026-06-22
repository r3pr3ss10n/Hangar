<script setup lang="ts">
import {
  UserPlus,
  MoreHorizontal,
  KeyRound,
  Trash2,
  ShieldCheck,
  User as UserIco,
  Loader2,
} from 'lucide-vue-next'
import type { User, Role } from '~/types/api'
import { useAuthStore } from '~/stores/auth'
import { useToast } from '~/composables/useToast'
import { useI18n } from '~/composables/useI18n'
import { formatDate } from '~/lib/format'
import { cn } from '~/lib/utils'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import { Badge } from '~/components/ui/badge'
import { Avatar } from '~/components/ui/avatar'
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

const api = useApi()
const auth = useAuthStore()
const { success, error: toastError } = useToast()
const { t } = useI18n()

const users = ref<User[]>([])
const loading = ref(false)

async function refresh() {
  loading.value = true
  try {
    users.value = (await api.listUsers()).users
  } catch (err) {
    toastError(t('users.failedLoad'), api.errorMessage(err))
  } finally {
    loading.value = false
  }
}
onMounted(refresh)

// ---- create ----
const createOpen = ref(false)
const form = reactive({ username: '', password: '', role: 'user' as Role })
const creating = ref(false)

function openCreate() {
  form.username = ''
  form.password = ''
  form.role = 'user'
  createOpen.value = true
}

async function submitCreate() {
  if (!form.username.trim() || form.password.length < 8) {
    toastError(t('users.checkForm'), t('users.checkFormDesc'))
    return
  }
  creating.value = true
  try {
    await api.createUser(form.username.trim(), form.password, form.role)
    success(t('users.created', { name: form.username.trim() }))
    createOpen.value = false
    await refresh()
  } catch (err) {
    toastError(t('users.failedCreate'), api.errorMessage(err))
  } finally {
    creating.value = false
  }
}

// ---- set password ----
const pwOpen = ref(false)
const pwTarget = ref<User | null>(null)
const pwValue = ref('')
const pwSaving = ref(false)

function openPassword(u: User) {
  pwTarget.value = u
  pwValue.value = ''
  pwOpen.value = true
}

async function submitPassword() {
  if (!pwTarget.value) return
  if (pwValue.value.length < 8) {
    toastError(t('users.passwordTooShort'), t('users.mustBe8'))
    return
  }
  pwSaving.value = true
  try {
    await api.setUserPassword(pwTarget.value.id, pwValue.value)
    success(t('users.passwordUpdated', { name: pwTarget.value.username }))
    pwOpen.value = false
  } catch (err) {
    toastError(t('users.failedSetPassword'), api.errorMessage(err))
  } finally {
    pwSaving.value = false
  }
}

// ---- delete ----
const delOpen = ref(false)
const delTarget = ref<User | null>(null)
const deleting = ref(false)

function openDelete(u: User) {
  delTarget.value = u
  delOpen.value = true
}

async function confirmDelete() {
  if (!delTarget.value) return
  deleting.value = true
  try {
    await api.deleteUser(delTarget.value.id)
    success(t('users.deleted', { name: delTarget.value.username }))
    delOpen.value = false
    await refresh()
  } catch (err) {
    toastError(t('users.failedDelete'), api.errorMessage(err))
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div>
    <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-xl font-semibold tracking-tight">{{ t('users.title') }}</h1>
        <p class="mt-0.5 text-sm text-muted-foreground">{{ t('users.subtitle') }}</p>
      </div>
      <Button @click="openCreate">
        <UserPlus /> {{ t('users.newUser') }}
      </Button>
    </div>

    <div class="overflow-hidden rounded-xl border bg-card">
      <div class="hidden grid-cols-[1fr,7rem,9rem,3rem] items-center gap-3 border-b px-4 py-2.5 text-xs font-medium uppercase tracking-wide text-muted-foreground sm:grid">
        <span>{{ t('users.colUser') }}</span>
        <span>{{ t('users.colRole') }}</span>
        <span>{{ t('users.colCreated') }}</span>
        <span class="sr-only">{{ t('drive.colActions') }}</span>
      </div>

      <div v-if="loading">
        <div v-for="i in 4" :key="i" class="flex items-center gap-3 border-b px-4 py-3 last:border-0">
          <Skeleton class="h-8 w-8 rounded-full" />
          <Skeleton class="h-4 w-40" />
          <Skeleton class="ml-auto h-4 w-16" />
        </div>
      </div>

      <ul v-else class="divide-y">
        <li
          v-for="u in users"
          :key="u.id"
          class="grid grid-cols-[1fr,3rem] items-center gap-3 px-4 py-3 transition-colors hover:bg-accent/40 sm:grid-cols-[1fr,7rem,9rem,3rem]"
        >
          <div class="flex min-w-0 items-center gap-3">
            <Avatar :name="u.username" />
            <div class="min-w-0">
              <p class="truncate text-sm font-medium">
                {{ u.username }}
                <span v-if="u.id === auth.user?.id" class="text-xs font-normal text-muted-foreground">{{ t('users.you') }}</span>
              </p>
              <p class="text-xs text-muted-foreground sm:hidden">{{ t('role.' + u.role) }} · {{ formatDate(u.created_at) }}</p>
            </div>
          </div>
          <div class="hidden sm:block">
            <Badge :variant="u.role === 'admin' ? 'default' : 'muted'">
              <ShieldCheck v-if="u.role === 'admin'" class="h-3 w-3" />
              <UserIco v-else class="h-3 w-3" />
              {{ t('role.' + u.role) }}
            </Badge>
          </div>
          <span class="hidden text-sm text-muted-foreground sm:block">{{ formatDate(u.created_at) }}</span>
          <div class="flex justify-end">
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button variant="ghost" size="icon-sm" :aria-label="t('users.actions')">
                  <MoreHorizontal />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem @select="openPassword(u)">
                  <KeyRound /> {{ t('users.setPassword') }}
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  variant="destructive"
                  :disabled="u.id === auth.user?.id"
                  @select="openDelete(u)"
                >
                  <Trash2 /> {{ t('common.delete') }}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </li>
      </ul>
    </div>

    <!-- Create user -->
    <Dialog v-model:open="createOpen">
      <DialogContent class="max-w-sm">
        <DialogHeader>
          <DialogTitle>{{ t('users.createTitle') }}</DialogTitle>
          <DialogDescription>{{ t('users.createDesc') }}</DialogDescription>
        </DialogHeader>
        <div class="grid gap-4">
          <div class="grid gap-2">
            <Label for="new-username">{{ t('common.username') }}</Label>
            <Input id="new-username" v-model="form.username" autocomplete="off" />
          </div>
          <div class="grid gap-2">
            <Label for="new-password">{{ t('common.password') }}</Label>
            <Input id="new-password" v-model="form.password" type="password" autocomplete="new-password" />
            <p class="text-xs text-muted-foreground">{{ t('setup.atLeast8') }}</p>
          </div>
          <div class="grid gap-2">
            <Label>{{ t('users.role') }}</Label>
            <div class="grid grid-cols-2 gap-2">
              <button
                v-for="r in (['user', 'admin'] as Role[])"
                :key="r"
                type="button"
                :class="cn(
                  'flex items-center justify-center gap-2 rounded-md border px-3 py-2 text-sm font-medium capitalize transition-colors',
                  form.role === r ? 'border-primary bg-accent' : 'border-input hover:bg-accent/50',
                )"
                @click="form.role = r"
              >
                <ShieldCheck v-if="r === 'admin'" class="h-4 w-4" />
                <UserIco v-else class="h-4 w-4" />
                {{ t('role.' + r) }}
              </button>
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="createOpen = false">{{ t('common.cancel') }}</Button>
          <Button :disabled="creating" @click="submitCreate">
            <Loader2 v-if="creating" class="animate-spin" />
            {{ creating ? t('common.creating') : t('users.createUser') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Set password -->
    <Dialog v-model:open="pwOpen">
      <DialogContent class="max-w-sm">
        <DialogHeader>
          <DialogTitle>{{ t('users.setPasswordTitle') }}</DialogTitle>
          <DialogDescription>{{ t('users.setPasswordDesc', { name: pwTarget?.username ?? '' }) }}</DialogDescription>
        </DialogHeader>
        <div class="grid gap-2">
          <Label for="pw">{{ t('common.password') }}</Label>
          <Input id="pw" v-model="pwValue" type="password" autocomplete="new-password" @keyup.enter="submitPassword" />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="pwOpen = false">{{ t('common.cancel') }}</Button>
          <Button :disabled="pwSaving" @click="submitPassword">
            {{ pwSaving ? t('common.saving') : t('users.updatePassword') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Delete -->
    <AlertDialog v-model:open="delOpen">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{{ t('users.deleteTitle') }}</AlertDialogTitle>
          <AlertDialogDescription>
            {{ t('users.deleteDesc', { name: delTarget?.username ?? '' }) }}
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
  </div>
</template>
