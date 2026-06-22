<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { AvatarRoot, AvatarFallback } from 'reka-ui'
import { cn } from '~/lib/utils'

const props = defineProps<{
  name?: string
  class?: HTMLAttributes['class']
}>()

const initials = computed(() => {
  const n = (props.name ?? '').trim()
  if (!n) return '?'
  const parts = n.split(/\s+/)
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
})
</script>

<template>
  <AvatarRoot
    :class="cn(
      'relative flex h-8 w-8 shrink-0 select-none items-center justify-center overflow-hidden rounded-full bg-secondary text-secondary-foreground',
      props.class,
    )"
  >
    <AvatarFallback class="text-xs font-medium">{{ initials }}</AvatarFallback>
  </AvatarRoot>
</template>
