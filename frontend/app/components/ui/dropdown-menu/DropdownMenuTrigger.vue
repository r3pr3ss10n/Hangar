<script setup lang="ts">
import { DropdownMenuTrigger } from 'reka-ui'
defineOptions({ inheritAttrs: false })
</script>

<template>
  <DropdownMenuTrigger v-bind="$attrs">
    <slot />
  </DropdownMenuTrigger>
</template>
