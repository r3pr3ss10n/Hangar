<script setup lang="ts">
import { TooltipProvider } from '~/components/ui/tooltip'
</script>

<template>
  <TooltipProvider :delay-duration="300">
    <div class="relative flex min-h-screen flex-col">
      <!-- Subtle backdrop glow to lift the auth card off the page. -->
      <div
        class="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(60%_50%_at_50%_0%,hsl(var(--accent))_0%,transparent_70%)] opacity-60"
      />
      <div class="absolute right-4 top-4">
        <ThemeToggle />
      </div>

      <main class="flex flex-1 items-center justify-center px-4 py-12">
        <slot />
      </main>

      <Toaster />
    </div>
  </TooltipProvider>
</template>
