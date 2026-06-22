<script setup lang="ts">
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-vue-next'
import { useToast } from '~/composables/useToast'

const { toasts, dismiss } = useToast()

const icons = {
  default: Info,
  success: CheckCircle2,
  error: AlertCircle,
}
</script>

<template>
  <Teleport to="body">
    <div class="pointer-events-none fixed bottom-0 right-0 z-[100] flex w-full max-w-sm flex-col gap-2 p-4">
      <TransitionGroup
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="translate-y-2 opacity-0 sm:translate-x-2 sm:translate-y-0"
        enter-to-class="translate-y-0 opacity-100 sm:translate-x-0"
        leave-active-class="transition duration-150 ease-in absolute"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0 translate-x-2"
      >
        <div
          v-for="t in toasts"
          :key="t.id"
          class="pointer-events-auto flex items-start gap-3 rounded-lg border bg-popover/95 p-3 text-popover-foreground shadow-lg backdrop-blur"
        >
          <component
            :is="icons[t.variant]"
            class="mt-0.5 h-4 w-4 shrink-0"
            :class="{
              'text-emerald-500': t.variant === 'success',
              'text-destructive': t.variant === 'error',
              'text-muted-foreground': t.variant === 'default',
            }"
          />
          <div class="min-w-0 flex-1">
            <p class="text-sm font-medium leading-snug">{{ t.title }}</p>
            <p v-if="t.description" class="mt-0.5 break-words text-xs text-muted-foreground">
              {{ t.description }}
            </p>
          </div>
          <button
            class="shrink-0 rounded-md p-0.5 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
            @click="dismiss(t.id)"
          >
            <X class="h-3.5 w-3.5" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>
