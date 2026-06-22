<script setup lang="ts">
import {
  Cloud,
  CloudOff,
  Check,
  Sparkles,
  TriangleAlert,
} from 'lucide-vue-next'
import type { TelegramState } from '~/types/api'
import { useToast } from '~/composables/useToast'
import { useI18n } from '~/composables/useI18n'
import { useAuthStore } from '~/stores/auth'
import { Button } from '~/components/ui/button'
import { Badge } from '~/components/ui/badge'
import { Skeleton } from '~/components/ui/skeleton'
import TelegramLinkWizard from '~/components/TelegramLinkWizard.vue'
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '~/components/ui/card'
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

const api = useApi()
const { success } = useToast()
const { t } = useI18n()
const auth = useAuthStore()

const state = ref<TelegramState | null>(null)
const loading = ref(false)
const error = ref('')
const busy = ref(false)

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    state.value = await api.telegramStatus()
    // Keep the routing guard's cached flag in sync with the real status so the
    // mandatory onboarding step stays accurate after a link/unlink here.
    auth.telegramLinked = state.value.status === 'linked'
  } catch (err) {
    error.value = api.errorMessage(err, t('tg.failedLoadStatus'))
  } finally {
    loading.value = false
  }
}
onMounted(refresh)

async function onLinked() {
  success(t('tg.accountLinked'))
  await refresh()
}

const unlinkOpen = ref(false)
async function unlink() {
  busy.value = true
  try {
    await api.telegramUnlink()
    success(t('tg.accountUnlinked'))
    unlinkOpen.value = false
    await refresh()
  } catch (err) {
    error.value = api.errorMessage(err, t('tg.failedUnlink'))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-xl">
    <div class="mb-5">
      <h1 class="text-xl font-semibold tracking-tight">{{ t('tg.title') }}</h1>
      <p class="mt-0.5 text-sm text-muted-foreground">
        {{ t('tg.subtitle') }}
      </p>
    </div>

    <div v-if="loading" class="space-y-3">
      <Skeleton class="h-20 w-full rounded-xl" />
      <Skeleton class="h-48 w-full rounded-xl" />
    </div>

    <template v-else>
      <!-- Status -->
      <Card class="mb-4">
        <CardContent class="flex items-center gap-4 p-4">
          <div
            class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full"
            :class="state?.status === 'linked' ? 'bg-emerald-500/15 text-emerald-500' : 'bg-muted text-muted-foreground'"
          >
            <component :is="state?.status === 'linked' ? Cloud : CloudOff" class="h-5 w-5" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">{{ state?.status ? t('tg.status.' + state.status) : '' }}</span>
              <Badge v-if="state?.status === 'linked'" variant="success">
                <Check class="h-3 w-3" /> {{ t('tg.active') }}
              </Badge>
              <Badge v-if="state?.is_premium" variant="muted">
                <Sparkles class="h-3 w-3" /> {{ t('tg.premium') }}
              </Badge>
            </div>
            <p class="mt-0.5 truncate text-sm text-muted-foreground">
              {{ state?.status === 'linked'
                ? (state?.phone ? state.phone + ' · ' : '') + t('tg.readyToStore')
                : t('tg.noAccountLinked') }}
            </p>
          </div>
          <Button
            v-if="state?.status === 'linked'"
            variant="outline"
            size="sm"
            :disabled="busy"
            @click="unlinkOpen = true"
          >
            {{ t('tg.unlink') }}
          </Button>
        </CardContent>
      </Card>

      <!-- Unlink warning -->
      <div
        v-if="state?.status === 'linked'"
        class="flex items-start gap-3 rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm"
      >
        <TriangleAlert class="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
        <p class="text-amber-700 dark:text-amber-300">
          {{ t('tg.unlinkWarning') }}
        </p>
      </div>

      <!-- Wizard -->
      <Card v-else>
        <CardHeader>
          <CardTitle class="text-base">{{ t('tg.linkAccount') }}</CardTitle>
          <CardDescription>{{ t('tg.linkAccountDesc') }}</CardDescription>
        </CardHeader>
        <CardContent>
          <TelegramLinkWizard @linked="onLinked" />
        </CardContent>
      </Card>
    </template>

    <!-- Unlink confirm -->
    <AlertDialog v-model:open="unlinkOpen">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{{ t('tg.unlinkTitle') }}</AlertDialogTitle>
          <AlertDialogDescription>
            {{ t('tg.unlinkDesc') }}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>{{ t('common.cancel') }}</AlertDialogCancel>
          <AlertDialogAction variant="destructive" :disabled="busy" @click="unlink">
            {{ busy ? t('tg.unlinking') : t('tg.unlink') }}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>
