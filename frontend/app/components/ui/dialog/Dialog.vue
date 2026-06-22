<script setup lang="ts">
import { DialogRoot } from 'reka-ui'
// Pass open / update:open (v-model:open) and modal through untouched.
defineOptions({ inheritAttrs: false })
</script>

<template>
  <DialogRoot v-bind="$attrs">
    <slot />
  </DialogRoot>
</template>
