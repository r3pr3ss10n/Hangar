<script setup lang="ts">
import { DropdownMenuRoot } from 'reka-ui'
defineOptions({ inheritAttrs: false })
</script>

<template>
  <DropdownMenuRoot v-bind="$attrs">
    <slot />
  </DropdownMenuRoot>
</template>
