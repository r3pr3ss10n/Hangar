<script setup lang="ts">
import { AlertDialogRoot } from 'reka-ui'
defineOptions({ inheritAttrs: false })
</script>

<template>
  <AlertDialogRoot v-bind="$attrs">
    <slot />
  </AlertDialogRoot>
</template>
