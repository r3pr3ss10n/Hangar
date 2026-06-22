<script setup lang="ts">
import { Tag as TagIcon } from 'lucide-vue-next'
import type { Folder, FileItem } from '~/types/api'
import { useToast } from '~/composables/useToast'
import { useI18n } from '~/composables/useI18n'
import { useLabels, tagDotClass } from '~/composables/useLabels'

const route = useRoute()
const api = useApi()
const { error: toastError } = useToast()
const { t } = useI18n()
const labels = useLabels()

const tagId = computed(() => String(route.params.id))
const tag = computed(() => labels.tags.value.find((x) => x.id === tagId.value) || null)

const folders = ref<Folder[]>([])
const files = ref<FileItem[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    await labels.load()
    const res = await api.tagItems(tagId.value)
    folders.value = res.folders
    files.value = res.files
  } catch (err) {
    toastError(t('tag.loadFailed'), api.errorMessage(err))
  } finally {
    loading.value = false
  }
}
watch(tagId, load, { immediate: true })

// Drop an item from the view as soon as this tag is removed from it.
function onUntag({ kind, id }: { kind: 'file' | 'folder'; id: string }) {
  if (kind === 'folder') folders.value = folders.value.filter((f) => f.id !== id)
  else files.value = files.value.filter((f) => f.id !== id)
}
</script>

<template>
  <div>
    <div class="mb-5 flex items-center gap-2.5">
      <span v-if="tag" class="h-3 w-3 shrink-0 rounded-full" :class="tagDotClass(tag.color)" />
      <h1 class="text-xl font-semibold tracking-tight">{{ tag?.name || t('tag.title') }}</h1>
    </div>

    <ResourceList
      :folders="folders"
      :files="files"
      :loading="loading"
      :empty-icon="TagIcon"
      :empty-title="t('tag.emptyTitle')"
      :untag-id="tagId"
      @untag="onUntag"
    />
  </div>
</template>
