<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { AlertDialogAction } from 'reka-ui'
import { cn } from '~/lib/utils'
import { buttonVariants, type ButtonVariants } from '~/components/ui/button'

defineOptions({ inheritAttrs: false })
const props = defineProps<{
  variant?: ButtonVariants['variant']
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <AlertDialogAction
    v-bind="$attrs"
    :class="cn(buttonVariants({ variant: props.variant ?? 'default' }), props.class)"
  >
    <slot />
  </AlertDialogAction>
</template>
