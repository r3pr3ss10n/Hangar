<script setup lang="ts">
import { Loader2 } from 'lucide-vue-next'
import { useAuthStore } from '~/stores/auth'
import { useI18n } from '~/composables/useI18n'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
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
const api = useApi()
const router = useRouter()

const username = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function onSubmit() {
  error.value = ''
  submitting.value = true
  try {
    await auth.login(username.value, password.value)
    router.push('/')
  } catch (err) {
    error.value = api.errorMessage(err, t('login.invalid'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="w-full max-w-sm">
    <div class="mb-6 flex flex-col items-center text-center">
      <div class="mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
        <svg viewBox="0 0 24 24" class="h-6 w-6" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 3 3 8.5v7L12 21l9-5.5v-7L12 3Z" />
          <path d="M12 12 3 8.5M12 12l9-3.5M12 12v9" />
        </svg>
      </div>
    </div>

    <Card>
      <CardHeader class="text-center">
        <CardTitle class="text-xl">{{ t('login.welcome') }}</CardTitle>
        <CardDescription>{{ t('login.subtitle') }}</CardDescription>
      </CardHeader>
      <CardContent>
        <form class="space-y-4" @submit.prevent="onSubmit">
          <div class="grid gap-2">
            <Label for="username">{{ t('common.username') }}</Label>
            <Input id="username" v-model="username" autocomplete="username" required />
          </div>
          <div class="grid gap-2">
            <Label for="password">{{ t('common.password') }}</Label>
            <Input
              id="password"
              v-model="password"
              type="password"
              autocomplete="current-password"
              required
            />
          </div>
          <p v-if="error" class="text-sm text-destructive">{{ error }}</p>
          <Button type="submit" class="w-full" :disabled="submitting">
            <Loader2 v-if="submitting" class="animate-spin" />
            {{ submitting ? t('login.signingIn') : t('login.signIn') }}
          </Button>
        </form>
      </CardContent>
    </Card>
  </div>
</template>
