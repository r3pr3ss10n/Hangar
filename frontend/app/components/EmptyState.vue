<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  icon?: Component
  title: string
  description?: string
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center px-6 py-16 text-center">
    <div
      v-if="icon"
      class="mb-4 flex h-12 w-12 items-center justify-center rounded-full border bg-muted/40 text-muted-foreground"
    >
      <component :is="icon" class="h-5 w-5" />
    </div>
    <p class="text-sm font-medium text-foreground">{{ title }}</p>
    <p v-if="description" class="mt-1 max-w-xs text-sm text-muted-foreground">{{ description }}</p>
    <div class="mt-4">
      <slot />
    </div>
  </div>
</template>
