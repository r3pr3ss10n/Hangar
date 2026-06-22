<script setup lang="ts">
import { DialogTrigger } from 'reka-ui'
defineOptions({ inheritAttrs: false })
</script>

<template>
  <DialogTrigger v-bind="$attrs">
    <slot />
  </DialogTrigger>
</template>
