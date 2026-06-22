<script setup lang="ts">
import { DialogClose } from 'reka-ui'
defineOptions({ inheritAttrs: false })
</script>

<template>
  <DialogClose v-bind="$attrs">
    <slot />
  </DialogClose>
</template>
