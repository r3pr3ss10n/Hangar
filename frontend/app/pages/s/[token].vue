<script setup lang="ts">
import { Download, Loader2, Link2Off } from 'lucide-vue-next'
import type { SharedFile } from '~/types/api'
import { useI18n } from '~/composables/useI18n'
import { formatSize, formatDate } from '~/lib/format'
import { Button } from '~/components/ui/button'
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '~/components/ui/card'

definePageMeta({ layout: 'auth' })

const route = useRoute()
const api = useApi()
const { t } = useI18n()

const token = computed(() => String(route.params.token))

const file = ref<SharedFile | null>(null)
const loading = ref(true)
const failed = ref(false)
const thumbFailed = ref(false)

onMounted(async () => {
  try {
    file.value = await api.shareInfo(token.value)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

const downloadUrl = computed(() => api.shareDownloadUrl(token.value))
const showThumb = computed(() => !!file.value?.has_thumb && !thumbFailed.value)
</script>

<template>
  <div class="w-full max-w-sm">
    <!-- Loading -->
    <div v-if="loading" class="flex justify-center py-16">
      <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
    </div>

    <!-- Unavailable / expired -->
    <Card v-else-if="failed || !file">
      <CardHeader class="items-center text-center">
        <div class="mb-1 flex h-11 w-11 items-center justify-center rounded-full border bg-muted/40 text-muted-foreground">
          <Link2Off class="h-5 w-5" />
        </div>
        <CardTitle class="text-base">{{ t('sharePage.notFoundTitle') }}</CardTitle>
        <CardDescription>{{ t('sharePage.notFoundDesc') }}</CardDescription>
      </CardHeader>
    </Card>

    <!-- Shared file -->
    <Card v-else>
      <CardContent class="flex flex-col items-center gap-4 p-6 text-center">
        <div class="flex aspect-square w-28 items-center justify-center overflow-hidden rounded-xl border bg-card">
          <img
            v-if="showThumb"
            :src="api.shareThumbUrl(token)"
            :alt="file.name"
            class="h-full w-full object-cover"
            @error="thumbFailed = true"
          >
          <FileIcon v-else :name="file.name" :mime="file.mime" big />
        </div>

        <div class="min-w-0">
          <p class="break-words text-sm font-medium">{{ file.name }}</p>
          <p class="mt-0.5 text-sm text-muted-foreground">{{ formatSize(file.size) }}</p>
          <p v-if="file.expires_at" class="mt-1 text-xs text-muted-foreground">
            {{ t('sharePage.expiresOn', { date: formatDate(file.expires_at) }) }}
          </p>
        </div>

        <Button as-child class="w-full">
          <a :href="downloadUrl" :download="file.name">
            <Download /> {{ t('sharePage.download') }}
          </a>
        </Button>

        <p class="text-xs text-muted-foreground/70">{{ t('sharePage.sharedVia') }}</p>
      </CardContent>
    </Card>
  </div>
</template>
