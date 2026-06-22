<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { AlertDialogCancel } from 'reka-ui'
import { cn } from '~/lib/utils'
import { buttonVariants } from '~/components/ui/button'

defineOptions({ inheritAttrs: false })
const props = defineProps<{ class?: HTMLAttributes['class'] }>()
</script>

<template>
  <AlertDialogCancel
    v-bind="$attrs"
    :class="cn(buttonVariants({ variant: 'outline' }), props.class)"
  >
    <slot />
  </AlertDialogCancel>
</template>
