<script setup lang="ts">
import { useLabels, tagDotClass } from '~/composables/useLabels'

// Renders an item's tag dots (coloured) for any listing.
const props = defineProps<{ kind: 'file' | 'folder'; id: string }>()

const labels = useLabels()
const tags = computed(() => labels.tagsFor(props.kind, props.id))
</script>

<template>
  <span v-if="tags.length" class="flex shrink-0 items-center gap-1">
    <span
      v-for="tg in tags"
      :key="tg.id"
      class="h-2 w-2 rounded-full"
      :class="tagDotClass(tg.color)"
      :title="tg.name"
    />
  </span>
</template>
