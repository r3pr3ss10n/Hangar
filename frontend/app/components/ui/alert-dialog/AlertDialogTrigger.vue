<script setup lang="ts">
import { AlertDialogTrigger } from 'reka-ui'
defineOptions({ inheritAttrs: false })
</script>

<template>
  <AlertDialogTrigger v-bind="$attrs">
    <slot />
  </AlertDialogTrigger>
</template>
