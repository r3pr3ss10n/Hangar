<script setup lang="ts">
import { Check, Plus, Trash2, Loader2 } from 'lucide-vue-next'
import { useToast } from '~/composables/useToast'
import { useI18n } from '~/composables/useI18n'
import { useLabels, TAG_COLORS, tagDotClass } from '~/composables/useLabels'
import { cn } from '~/lib/utils'
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

const { error: toastError } = useToast()
const { t } = useI18n()
const labels = useLabels()

const newName = ref('')
const newColor = ref<string>('blue')
const creating = ref(false)

const assigned = (tagId: string) =>
  props.target ? labels.hasTag(props.target.kind, props.target.id, tagId) : false

async function toggle(tagId: string) {
  if (!props.target) return
  try {
    await labels.assignTag(props.target.kind, props.target.id, tagId, !assigned(tagId))
  } catch (err) {
    toastError(t('tag.assignFailed'), useApi().errorMessage(err))
  }
}

async function create() {
  const name = newName.value.trim()
  if (!name || creating.value) return
  creating.value = true
  try {
    const tag = await labels.createTag(name, newColor.value)
    newName.value = ''
    // Immediately apply the new tag to the current item.
    if (props.target) await labels.assignTag(props.target.kind, props.target.id, tag.id, true)
  } catch (err) {
    toastError(t('tag.createFailed'), useApi().errorMessage(err))
  } finally {
    creating.value = false
  }
}

async function remove(tagId: string) {
  try {
    await labels.removeTag(tagId)
  } catch (err) {
    toastError(t('tag.deleteFailed'), useApi().errorMessage(err))
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="max-w-sm gap-3">
      <DialogHeader>
        <DialogTitle>{{ t('tag.title') }}</DialogTitle>
        <DialogDescription class="truncate">{{ target?.name }}</DialogDescription>
      </DialogHeader>

      <!-- Existing tags (click to toggle on this item) -->
      <div class="max-h-56 space-y-0.5 overflow-y-auto">
        <p v-if="!labels.tags.value.length" class="text-sm text-muted-foreground">
          {{ t('tag.none') }}
        </p>
        <div
          v-for="tag in labels.tags.value"
          :key="tag.id"
          class="group flex items-center gap-2.5 rounded-md px-2 py-1.5 hover:bg-accent/50"
        >
          <button
            type="button"
            class="flex min-w-0 flex-1 items-center gap-2.5 text-left"
            @click="toggle(tag.id)"
          >
            <span
              class="flex h-4 w-4 shrink-0 items-center justify-center rounded-full"
              :class="tagDotClass(tag.color)"
            >
              <Check v-if="assigned(tag.id)" class="h-3 w-3 text-white" />
            </span>
            <span class="min-w-0 flex-1 truncate text-sm">{{ tag.name }}</span>
          </button>
          <button
            type="button"
            class="shrink-0 rounded p-1 text-muted-foreground opacity-0 transition-opacity hover:text-destructive group-hover:opacity-100"
            :aria-label="t('tag.delete')"
            @click="remove(tag.id)"
          >
            <Trash2 class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>

      <!-- Create a new tag -->
      <div class="space-y-2 border-t pt-3">
        <div class="flex items-center gap-1.5">
          <button
            v-for="c in TAG_COLORS"
            :key="c"
            type="button"
            class="h-5 w-5 rounded-full transition-transform"
            :class="cn(tagDotClass(c), newColor === c ? 'ring-2 ring-offset-2 ring-offset-background ring-foreground/40 scale-110' : '')"
            :aria-label="c"
            @click="newColor = c"
          />
        </div>
        <div class="flex gap-2">
          <Input
            v-model="newName"
            :placeholder="t('tag.newPlaceholder')"
            @keyup.enter="create"
          />
          <Button class="shrink-0" :disabled="creating || !newName.trim()" @click="create">
            <Loader2 v-if="creating" class="animate-spin" />
            <Plus v-else />
            {{ t('tag.create') }}
          </Button>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
