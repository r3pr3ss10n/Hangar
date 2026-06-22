<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { DropdownMenuItem } from 'reka-ui'
import { cn } from '~/lib/utils'

defineOptions({ inheritAttrs: false })
const props = defineProps<{
  class?: HTMLAttributes['class']
  variant?: 'default' | 'destructive'
}>()
</script>

<template>
  <DropdownMenuItem
    v-bind="$attrs"
    :class="cn(
      'relative flex cursor-pointer select-none items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50 [&_svg]:size-4 [&_svg]:shrink-0',
      props.variant === 'destructive'
        ? 'text-destructive focus:bg-destructive/10 focus:text-destructive'
        : '',
      props.class,
    )"
  >
    <slot />
  </DropdownMenuItem>
</template>
