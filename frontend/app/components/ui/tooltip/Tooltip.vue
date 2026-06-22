<script setup lang="ts">
import { TooltipRoot } from 'reka-ui'
defineOptions({ inheritAttrs: false })
</script>

<template>
  <TooltipRoot v-bind="$attrs">
    <slot />
  </TooltipRoot>
</template>
