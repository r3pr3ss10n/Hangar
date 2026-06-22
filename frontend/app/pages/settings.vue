<script setup lang="ts">
import { Image as ImageIcon, Languages, Check, ChevronsUpDown, Loader2 } from 'lucide-vue-next'
import type { Settings } from '~/types/api'
import { useToast } from '~/composables/useToast'
import { useI18n } from '~/composables/useI18n'
import { Card } from '~/components/ui/card'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from '~/components/ui/dropdown-menu'
import { Skeleton } from '~/components/ui/skeleton'

const api = useApi()
const { error: toastError } = useToast()
const { t, locale, locales, setLocale } = useI18n()

const currentLocale = computed(() => locales.find((l) => l.code === locale.value) ?? locales[0])

const settings = ref<Settings | null>(null)
const loading = ref(true)
const saving = ref(false)

onMounted(async () => {
  try {
    settings.value = await api.getSettings()
  } catch (err) {
    toastError(t('settings.couldNotLoad'), api.errorMessage(err))
  } finally {
    loading.value = false
  }
})

async function setGenerateThumbnails(value: boolean) {
  if (!settings.value || saving.value) return
  const prev = settings.value.generate_thumbnails
  // Optimistic: reflect immediately, roll back on failure.
  settings.value.generate_thumbnails = value
  saving.value = true
  try {
    settings.value = await api.updateSettings({ generate_thumbnails: value })
  } catch (err) {
    if (settings.value) settings.value.generate_thumbnails = prev
    toastError(t('settings.couldNotSave'), api.errorMessage(err))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-2xl">
    <div class="mb-5">
      <h1 class="text-xl font-semibold tracking-tight">{{ t('settings.title') }}</h1>
      <p class="mt-0.5 text-sm text-muted-foreground">{{ t('settings.subtitle') }}</p>
    </div>

    <Card class="divide-y">
      <!-- Language -->
      <div class="flex items-center justify-between gap-4 p-5">
        <div class="flex min-w-0 gap-3">
          <Languages class="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" />
          <div class="min-w-0">
            <p class="text-sm font-medium">{{ t('settings.interfaceLanguage') }}</p>
            <p class="mt-0.5 text-sm text-muted-foreground">
              {{ t('settings.interfaceLanguageDesc') }}
            </p>
          </div>
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <button
              type="button"
              class="flex h-9 shrink-0 items-center gap-2 rounded-md border bg-background px-3 text-sm outline-none ring-ring ring-offset-2 ring-offset-background transition-colors hover:bg-accent/50 focus-visible:ring-2"
            >
              <span class="text-base leading-none">{{ currentLocale.flag }}</span>
              <span class="font-medium">{{ currentLocale.label }}</span>
              <ChevronsUpDown class="h-4 w-4 text-muted-foreground" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="min-w-[12rem]">
            <DropdownMenuItem
              v-for="l in locales"
              :key="l.code"
              @select="setLocale(l.code)"
            >
              <span class="text-base leading-none">{{ l.flag }}</span>
              <span class="flex-1">{{ l.label }}</span>
              <Check v-if="locale === l.code" class="h-4 w-4 text-primary" />
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <!-- Generate thumbnails -->
      <div class="p-5">
        <div v-if="loading" class="flex items-center gap-3">
          <Skeleton class="h-5 w-5 shrink-0 rounded" />
          <Skeleton class="h-5 w-56" />
          <Skeleton class="ml-auto h-6 w-11 rounded-full" />
        </div>

        <div v-else-if="settings" class="flex items-start justify-between gap-4">
          <div class="flex min-w-0 gap-3">
            <ImageIcon class="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" />
            <div class="min-w-0">
              <p class="text-sm font-medium">{{ t('settings.generateThumbnails') }}</p>
              <p class="mt-0.5 text-sm text-muted-foreground">
                {{ t('settings.generateThumbnailsDesc') }}
              </p>
            </div>
          </div>

          <!-- Switch -->
          <button
            type="button"
            role="switch"
            :aria-checked="settings.generate_thumbnails"
            :disabled="saving"
            class="relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors outline-none ring-ring ring-offset-2 ring-offset-background focus-visible:ring-2 disabled:opacity-60"
            :class="settings.generate_thumbnails ? 'bg-primary' : 'bg-input'"
            @click="setGenerateThumbnails(!settings.generate_thumbnails)"
          >
            <span
              class="inline-block h-5 w-5 transform rounded-full bg-background shadow transition-transform"
              :class="settings.generate_thumbnails ? 'translate-x-[1.375rem]' : 'translate-x-0.5'"
            />
            <Loader2
              v-if="saving"
              class="absolute -right-6 h-4 w-4 animate-spin text-muted-foreground"
            />
          </button>
        </div>
      </div>
    </Card>
  </div>
</template>
