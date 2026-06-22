<script setup lang="ts">
import { UploadCloud } from 'lucide-vue-next'
import { useDriveStore } from '~/stores/drive'
import { useI18n } from '~/composables/useI18n'
import { TooltipProvider } from '~/components/ui/tooltip'

const drive = useDriveStore()
const route = useRoute()
const { t } = useI18n()

// Hidden file input, shared by every "upload" trigger via drive.pickSignal.
const fileInput = ref<HTMLInputElement | null>(null)
watch(
  () => drive.pickSignal,
  () => fileInput.value?.click(),
)
function onPick(e: Event) {
  const target = e.target as HTMLInputElement
  if (target.files?.length) drive.startUploads(target.files)
  target.value = ''
}

// Full-window drag-and-drop, active only on the drive page. A depth counter
// keeps the overlay stable across dragenter/leave on nested children.
const dragging = ref(false)
let dragDepth = 0
const canDrop = computed(() => route.path === '/')

function hasFiles(e: DragEvent) {
  return Array.from(e.dataTransfer?.types ?? []).includes('Files')
}
function onDragEnter(e: DragEvent) {
  if (!canDrop.value || !hasFiles(e)) return
  dragDepth++
  dragging.value = true
}
function onDragOver(e: DragEvent) {
  if (canDrop.value && hasFiles(e)) e.preventDefault()
}
function onDragLeave() {
  if (!canDrop.value) return
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) dragging.value = false
}
function onDrop(e: DragEvent) {
  dragDepth = 0
  dragging.value = false
  if (!canDrop.value || !e.dataTransfer?.files?.length) return
  e.preventDefault()
  drive.startUploads(e.dataTransfer.files)
}
</script>

<template>
  <TooltipProvider :delay-duration="300">
    <div
      class="min-h-full"
      @dragenter="onDragEnter"
      @dragover="onDragOver"
      @dragleave="onDragLeave"
      @drop="onDrop"
    >
      <AppSidebar />

      <div class="flex min-h-screen flex-col md:pl-60">
        <AppTopbar />
        <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <slot />
        </main>
      </div>

      <!-- Drag overlay -->
      <Transition
        enter-active-class="transition-opacity duration-150"
        enter-from-class="opacity-0"
        leave-active-class="transition-opacity duration-150"
        leave-to-class="opacity-0"
      >
        <div
          v-if="dragging"
          class="pointer-events-none fixed inset-0 z-[60] flex items-center justify-center bg-background/70 backdrop-blur-sm md:pl-60"
        >
          <div class="flex flex-col items-center gap-4 rounded-2xl border-2 border-dashed border-primary/40 bg-card/80 px-16 py-12 shadow-2xl">
            <UploadCloud class="h-10 w-10 text-primary" />
            <p class="text-lg font-medium">{{ t('drag.dropToUpload') }}</p>
            <p class="text-sm text-muted-foreground">{{ t('drag.toFolder', { name: drive.crumbs[drive.crumbs.length - 1].name }) }}</p>
          </div>
        </div>
      </Transition>

      <input ref="fileInput" type="file" multiple class="hidden" @change="onPick">

      <UploadDock />
      <CommandPalette />
      <Toaster />
    </div>
  </TooltipProvider>
</template>
