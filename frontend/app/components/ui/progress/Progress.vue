<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { ProgressRoot, ProgressIndicator } from 'reka-ui'
import { cn } from '~/lib/utils'

const props = withDefaults(
  defineProps<{ modelValue?: number; class?: HTMLAttributes['class'] }>(),
  { modelValue: 0 },
)
</script>

<template>
  <ProgressRoot
    :model-value="props.modelValue"
    :class="cn('relative h-1.5 w-full overflow-hidden rounded-full bg-muted', props.class)"
  >
    <ProgressIndicator
      class="h-full w-full flex-1 rounded-full bg-primary transition-all duration-300"
      :style="`transform: translateX(-${100 - (props.modelValue ?? 0)}%)`"
    />
  </ProgressRoot>
</template>
