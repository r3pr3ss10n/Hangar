<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { TooltipContent, TooltipPortal } from 'reka-ui'
import { cn } from '~/lib/utils'

defineOptions({ inheritAttrs: false })
withDefaults(defineProps<{ class?: HTMLAttributes['class']; sideOffset?: number }>(), {
  sideOffset: 6,
})
</script>

<template>
  <TooltipPortal>
    <TooltipContent
      :side-offset="sideOffset"
      v-bind="$attrs"
      :class="cn(
        'z-50 overflow-hidden rounded-md border bg-popover px-2.5 py-1.5 text-xs text-popover-foreground shadow-md data-[state=delayed-open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=delayed-open]:fade-in-0 data-[state=delayed-open]:zoom-in-95',
        $props.class,
      )"
    >
      <slot />
    </TooltipContent>
  </TooltipPortal>
</template>
