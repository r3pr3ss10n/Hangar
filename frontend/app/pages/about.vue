<script setup lang="ts">
import { Github, Scale } from 'lucide-vue-next'
import { useI18n } from '~/composables/useI18n'
import { Card } from '~/components/ui/card'

const { t } = useI18n()

const GITHUB_URL = 'https://github.com/r3pr3ss10n/Hangar'
const AUTHOR_URL = 'https://github.com/r3pr3ss10n'
const AUTHOR = 'r3pr3ss10n'
const VERSION = '1.0'
</script>

<template>
  <div class="mx-auto max-w-2xl">
    <div class="mb-5">
      <h1 class="text-xl font-semibold tracking-tight">{{ t('about.title') }}</h1>
      <p class="mt-0.5 text-sm text-muted-foreground">{{ t('about.tagline') }}</p>
    </div>

    <Card class="overflow-hidden">
      <!-- Hero -->
      <div class="flex flex-col items-center gap-3 px-6 pb-6 pt-8 text-center">
        <div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-sm">
          <svg viewBox="0 0 24 24" class="h-9 w-9" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3 3 8.5v7L12 21l9-5.5v-7L12 3Z" />
            <path d="M12 12 3 8.5M12 12l9-3.5M12 12v9" />
          </svg>
        </div>
        <div>
          <h2 class="text-2xl font-semibold tracking-tight">Hangar</h2>
          <p class="mt-1 text-xs font-medium text-muted-foreground">
            {{ t('about.version', { version: VERSION }) }}
          </p>
        </div>
        <p class="mt-1 max-w-md text-sm leading-relaxed text-muted-foreground">
          {{ t('about.description') }}
        </p>

        <a
          :href="GITHUB_URL"
          target="_blank"
          rel="noopener noreferrer"
          class="mt-2 inline-flex h-10 items-center gap-2 rounded-md bg-primary px-5 text-sm font-medium text-primary-foreground outline-none ring-ring ring-offset-2 ring-offset-background transition-opacity hover:opacity-90 focus-visible:ring-2"
        >
          <Github class="h-4 w-4" />
          {{ t('about.viewOnGithub') }}
        </a>
      </div>

      <!-- Footer details -->
      <div class="divide-y border-t text-sm">
        <div class="flex items-center justify-between gap-4 px-6 py-3.5">
          <span class="text-muted-foreground">{{ t('about.author') }}</span>
          <a
            :href="AUTHOR_URL"
            target="_blank"
            rel="noopener noreferrer"
            class="font-medium text-primary hover:underline"
          >{{ AUTHOR }}</a>
        </div>
        <div class="flex items-center justify-between gap-4 px-6 py-3.5">
          <span class="flex items-center gap-2 text-muted-foreground">
            <Scale class="h-4 w-4 shrink-0" />
            {{ t('about.license') }}
          </span>
        </div>
      </div>
    </Card>
  </div>
</template>
