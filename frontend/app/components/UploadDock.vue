<script setup lang="ts">
import { CheckCircle2, AlertCircle, X, ChevronDown, Loader2, Ban } from 'lucide-vue-next'
import { useDriveStore } from '~/stores/drive'
import { useI18n } from '~/composables/useI18n'
import { Progress } from '~/components/ui/progress'
import { Button } from '~/components/ui/button'
import { formatSize } from '~/lib/format'

const drive = useDriveStore()
const { t } = useI18n()
const collapsed = ref(false)

const title = computed(() => {
  const active = drive.activeUploads
  if (active > 0) return t('upload.uploadingN', { n: active })
  return t('upload.uploads')
})
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="translate-y-4 opacity-0"
      leave-active-class="transition duration-150 ease-in"
      leave-to-class="translate-y-4 opacity-0"
    >
      <div
        v-if="drive.uploads.length"
        class="fixed bottom-4 right-4 z-50 w-[22rem] max-w-[calc(100vw-2rem)] overflow-hidden rounded-xl border bg-card shadow-2xl"
      >
        <div class="flex items-center justify-between gap-2 border-b px-4 py-2.5">
          <div class="flex items-center gap-2 text-sm font-medium">
            <Loader2 v-if="drive.activeUploads" class="h-4 w-4 animate-spin text-muted-foreground" />
            <span>{{ title }}</span>
          </div>
          <div class="flex items-center gap-1">
            <Button variant="ghost" size="icon-sm" :aria-label="t('upload.collapse')" @click="collapsed = !collapsed">
              <ChevronDown class="transition-transform" :class="collapsed ? 'rotate-180' : ''" />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              :aria-label="t('upload.clear')"
              :disabled="drive.activeUploads > 0"
              @click="drive.clearFinishedUploads()"
            >
              <X />
            </Button>
          </div>
        </div>

        <div v-show="!collapsed" class="max-h-72 space-y-1 overflow-y-auto p-2">
          <div
            v-for="u in drive.uploads"
            :key="u.id"
            class="rounded-lg px-2 py-2 transition-colors hover:bg-accent/40"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="truncate text-sm">{{ u.name }}</span>
              <div class="flex shrink-0 items-center gap-1.5">
                <CheckCircle2 v-if="u.status === 'done'" class="h-4 w-4 text-emerald-500" />
                <AlertCircle v-else-if="u.status === 'error'" class="h-4 w-4 text-destructive" />
                <Ban v-else-if="u.status === 'canceled'" class="h-4 w-4 text-muted-foreground" />
                <span v-else class="font-mono text-xs text-muted-foreground">
                  {{ u.progress < 100 ? u.progress + '%' : '···' }}
                </span>
                <Button
                  v-if="u.status === 'uploading'"
                  variant="ghost"
                  size="icon-sm"
                  class="h-6 w-6"
                  :aria-label="t('upload.cancelUpload')"
                  @click="drive.cancelUpload(u.id)"
                >
                  <X class="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
            <Progress
              v-if="u.status === 'uploading'"
              :model-value="u.progress"
              class="mt-1.5 h-1"
            />
            <p v-else-if="u.status === 'error'" class="mt-0.5 truncate text-xs text-destructive">
              {{ u.error }}
            </p>
            <p v-else class="mt-0.5 text-xs text-muted-foreground">
              {{ u.status === 'canceled' ? t('upload.canceled') : formatSize(u.size) }}
            </p>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
