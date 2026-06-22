<script setup lang="ts">
import {
  FileText,
  FileImage,
  FileVideo,
  FileAudio,
  FileArchive,
  FileCode,
  FileSpreadsheet,
  File as FileIco,
} from 'lucide-vue-next'

const props = defineProps<{ name: string; mime?: string; big?: boolean }>()

// Map MIME first, then fall back to extension. Each kind gets a quiet accent so
// the list scans by colour without becoming noisy.
const kinds = {
  image: { icon: FileImage, class: 'text-violet-500 dark:text-violet-400' },
  video: { icon: FileVideo, class: 'text-rose-500 dark:text-rose-400' },
  audio: { icon: FileAudio, class: 'text-amber-500 dark:text-amber-400' },
  archive: { icon: FileArchive, class: 'text-orange-500 dark:text-orange-400' },
  code: { icon: FileCode, class: 'text-sky-500 dark:text-sky-400' },
  sheet: { icon: FileSpreadsheet, class: 'text-emerald-500 dark:text-emerald-400' },
  doc: { icon: FileText, class: 'text-blue-500 dark:text-blue-400' },
  generic: { icon: FileIco, class: 'text-muted-foreground' },
} as const

const archiveExt = ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'tgz']
const codeExt = ['js', 'ts', 'tsx', 'jsx', 'vue', 'go', 'py', 'rs', 'java', 'c', 'cpp', 'h', 'json', 'yaml', 'yml', 'toml', 'sh', 'html', 'css', 'sql']
const sheetExt = ['csv', 'xls', 'xlsx', 'ods']
const docExt = ['pdf', 'doc', 'docx', 'txt', 'md', 'rtf', 'odt']

const kind = computed(() => {
  const mime = (props.mime ?? '').toLowerCase()
  if (mime.startsWith('image/')) return kinds.image
  if (mime.startsWith('video/')) return kinds.video
  if (mime.startsWith('audio/')) return kinds.audio

  const ext = props.name.split('.').pop()?.toLowerCase() ?? ''
  if (archiveExt.includes(ext)) return kinds.archive
  if (codeExt.includes(ext)) return kinds.code
  if (sheetExt.includes(ext)) return kinds.sheet
  if (docExt.includes(ext)) return kinds.doc
  if (mime.includes('zip') || mime.includes('compressed')) return kinds.archive
  if (mime.startsWith('text/')) return kinds.doc
  return kinds.generic
})
</script>

<template>
  <component
    :is="kind.icon"
    class="shrink-0"
    :class="[big ? 'h-10 w-10' : 'h-4 w-4', kind.class]"
  />
</template>
