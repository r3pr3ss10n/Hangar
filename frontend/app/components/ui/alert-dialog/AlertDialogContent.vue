<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { AlertDialogContent, AlertDialogOverlay, AlertDialogPortal } from 'reka-ui'
import { cn } from '~/lib/utils'

defineOptions({ inheritAttrs: false })
const props = defineProps<{ class?: HTMLAttributes['class'] }>()
</script>

<template>
  <AlertDialogPortal>
    <AlertDialogOverlay class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm" />
    <AlertDialogContent
      v-bind="$attrs"
      :class="cn(
        'fixed left-1/2 top-1/2 z-50 grid w-full max-w-md -translate-x-1/2 -translate-y-1/2 gap-4 border bg-card p-6 shadow-lg sm:rounded-xl',
        props.class,
      )"
    >
      <slot />
    </AlertDialogContent>
  </AlertDialogPortal>
</template>
