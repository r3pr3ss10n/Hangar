<script setup lang="ts">
import { useAuthStore } from '~/stores/auth'
import { useI18n } from '~/composables/useI18n'
import { useToast } from '~/composables/useToast'
import TelegramLinkWizard from '~/components/TelegramLinkWizard.vue'
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from '~/components/ui/card'

definePageMeta({ layout: 'auth' })

const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()
const { success } = useToast()

async function onLinked() {
  // Update the cached flag so the routing guard lets us through to the app.
  auth.telegramLinked = true
  success(t('tg.accountLinked'))
  router.push('/')
}
</script>

<template>
  <div class="w-full max-w-sm">
    <div class="mb-6 flex flex-col items-center text-center">
      <div
        class="mb-3 flex h-14 w-14 items-center justify-center rounded-full shadow-sm"
        style="background: linear-gradient(180deg, #2aabee 0%, #229ed9 100%)"
      >
        <svg viewBox="0 0 24 24" class="h-7 w-7 text-white" fill="currentColor">
          <path d="M21.94 4.66a1.2 1.2 0 0 0-1.62-1.2L2.9 10.2c-1.06.41-1.05 1.92.02 2.32l4.3 1.6 1.66 5.02c.22.66 1.05.86 1.55.37l2.4-2.36 4.2 3.1c.55.4 1.33.1 1.47-.57l3.44-14.62.01-.4Zm-4.1 2.2-8.1 7.34a.9.9 0 0 0-.28.5l-.4 2.5c-.02.13-.2.14-.24.02l-1.2-3.96a.45.45 0 0 1 .19-.5l9.83-6.2c.27-.17.54.2.4.3Z" />
        </svg>
      </div>
      <span class="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {{ t('onboarding.step') }}
      </span>
    </div>

    <Card>
      <CardHeader class="text-center">
        <CardTitle class="text-xl">{{ t('onboarding.telegram.title') }}</CardTitle>
        <CardDescription>{{ t('onboarding.telegram.subtitle') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <TelegramLinkWizard @linked="onLinked" />
      </CardContent>
    </Card>

    <p class="mt-4 text-center text-xs text-muted-foreground">
      {{ t('onboarding.telegram.note') }}
    </p>
  </div>
</template>
