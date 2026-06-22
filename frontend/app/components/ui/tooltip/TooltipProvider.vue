<script setup lang="ts">
import { TooltipProvider } from 'reka-ui'
defineOptions({ inheritAttrs: false })
</script>

<template>
  <TooltipProvider v-bind="$attrs">
    <slot />
  </TooltipProvider>
</template>
