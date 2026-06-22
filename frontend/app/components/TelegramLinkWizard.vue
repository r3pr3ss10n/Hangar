<script setup lang="ts">
import { Loader2, Check, Phone, KeyRound, ShieldCheck } from 'lucide-vue-next'
import { useI18n } from '~/composables/useI18n'
import { cn } from '~/lib/utils'
import { Button } from '~/components/ui/button'
import { Input } from '~/components/ui/input'
import { Label } from '~/components/ui/label'
import CountrySelect from '~/components/CountrySelect.vue'
import { countryByIso, defaultCountryIso } from '~/lib/countries'

/**
 * The Telegram-style linking wizard: a phone → login code → 2FA password
 * stepper that drives the backend link state machine. It is shared between the
 * mandatory onboarding step and the admin Telegram settings page; it emits
 * `linked` once the account is connected and leaves all surrounding chrome
 * (cards, headings, toasts) to the parent.
 */
const emit = defineEmits<{ linked: [] }>()

const api = useApi()
const { t } = useI18n()

type Step = 'phone' | 'code' | 'password'
const step = ref<Step>('phone')
const linkId = ref('')

const country = ref(defaultCountryIso())
const nationalNumber = ref('')
const code = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)
// The 2FA step only exists once Telegram tells us the account has it enabled.
const needPassword = ref(false)

const dial = computed(() => countryByIso(country.value)?.dial ?? '')
// The full E.164 number Telegram expects, e.g. +15551234567.
const fullPhone = computed(() => '+' + dial.value + nationalNumber.value.replace(/\D/g, ''))

const steps = computed<{ key: Step; labelKey: string; icon: typeof Phone }[]>(() => [
  { key: 'phone', labelKey: 'tg.step.phone', icon: Phone },
  { key: 'code', labelKey: 'tg.step.code', icon: KeyRound },
  // The 2FA step is shown only when the account actually requires it.
  ...(needPassword.value
    ? [{ key: 'password' as Step, labelKey: 'tg.step.password', icon: ShieldCheck }]
    : []),
])
const stepIndex = computed(() => steps.value.findIndex((s) => s.key === step.value))

async function startLink() {
  error.value = ''
  if (!nationalNumber.value.replace(/\D/g, '')) {
    error.value = t('tg.enterPhone')
    return
  }
  busy.value = true
  try {
    linkId.value = (await api.telegramLinkStart(fullPhone.value)).link_id
    step.value = 'code'
  } catch (err) {
    error.value = api.errorMessage(err, t('tg.failedStartLink'))
  } finally {
    busy.value = false
  }
}

async function submitCode() {
  error.value = ''
  if (!code.value.trim()) {
    error.value = t('tg.enterCode')
    return
  }
  busy.value = true
  try {
    const res = await api.telegramLinkCode(linkId.value, code.value.trim())
    if (res.need_password) {
      needPassword.value = true
      step.value = 'password'
    } else {
      finish()
    }
  } catch (err) {
    error.value = api.errorMessage(err, t('tg.invalidCode'))
  } finally {
    busy.value = false
  }
}

async function submitPassword() {
  error.value = ''
  if (!password.value) {
    error.value = t('tg.enterPassword')
    return
  }
  busy.value = true
  try {
    await api.telegramLinkPassword(linkId.value, password.value)
    finish()
  } catch (err) {
    error.value = api.errorMessage(err, t('tg.invalidPassword'))
  } finally {
    busy.value = false
  }
}

function finish() {
  linkId.value = ''
  nationalNumber.value = ''
  code.value = ''
  password.value = ''
  needPassword.value = false
  step.value = 'phone'
  emit('linked')
}

async function cancel() {
  if (linkId.value) {
    try {
      await api.telegramLinkCancel(linkId.value)
    } catch {
      /* best-effort */
    }
  }
  linkId.value = ''
  code.value = ''
  password.value = ''
  needPassword.value = false
  step.value = 'phone'
  error.value = ''
}
</script>

<template>
  <div class="space-y-5">
    <!-- Stepper -->
    <div class="flex items-center">
      <template v-for="(s, i) in steps" :key="s.key">
        <div class="flex flex-col items-center gap-1.5">
          <div
            :class="cn(
              'flex h-8 w-8 items-center justify-center rounded-full border text-sm transition-colors',
              i < stepIndex ? 'border-primary bg-primary text-primary-foreground'
              : i === stepIndex ? 'border-primary text-foreground'
              : 'border-border text-muted-foreground',
            )"
          >
            <Check v-if="i < stepIndex" class="h-4 w-4" />
            <component :is="s.icon" v-else class="h-4 w-4" />
          </div>
          <span
            class="text-xs"
            :class="i === stepIndex ? 'font-medium text-foreground' : 'text-muted-foreground'"
          >{{ t(s.labelKey) }}</span>
        </div>
        <div
          v-if="i < steps.length - 1"
          class="mx-2 mb-5 h-px flex-1"
          :class="i < stepIndex ? 'bg-primary' : 'bg-border'"
        />
      </template>
    </div>

    <p v-if="error" class="text-sm text-destructive">{{ error }}</p>

    <!-- Phone -->
    <div v-if="step === 'phone'" class="space-y-3">
      <div class="grid gap-2">
        <Label>{{ t('tg.country') }}</Label>
        <CountrySelect v-model="country" />
      </div>
      <div class="grid gap-2">
        <Label for="phone">{{ t('tg.phoneNumber') }}</Label>
        <div
          class="flex h-9 w-full items-center rounded-md border border-input bg-transparent text-sm shadow-sm transition-colors focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2 focus-within:ring-offset-background"
        >
          <span class="select-none border-r border-input px-3 text-muted-foreground">+{{ dial }}</span>
          <input
            id="phone"
            v-model="nationalNumber"
            type="tel"
            inputmode="tel"
            autocomplete="tel-national"
            placeholder="555 123 4567"
            class="h-full flex-1 rounded-r-md bg-transparent px-3 outline-none placeholder:text-muted-foreground"
            @keyup.enter="startLink"
          >
        </div>
      </div>
      <Button class="w-full" :disabled="busy" @click="startLink">
        <Loader2 v-if="busy" class="animate-spin" />
        {{ busy ? t('tg.sending') : t('tg.sendCode') }}
      </Button>
    </div>

    <!-- Code -->
    <div v-else-if="step === 'code'" class="space-y-3">
      <div class="grid gap-2">
        <Label for="code">{{ t('tg.loginCode') }}</Label>
        <Input id="code" v-model="code" inputmode="numeric" placeholder="12345" @keyup.enter="submitCode" />
        <p class="text-xs text-muted-foreground">{{ t('tg.codeHint') }}</p>
      </div>
      <div class="flex gap-2">
        <Button class="flex-1" :disabled="busy" @click="submitCode">
          <Loader2 v-if="busy" class="animate-spin" />
          {{ busy ? t('tg.verifying') : t('tg.verifyCode') }}
        </Button>
        <Button variant="outline" @click="cancel">{{ t('common.cancel') }}</Button>
      </div>
    </div>

    <!-- 2FA -->
    <div v-else-if="step === 'password'" class="space-y-3">
      <div class="grid gap-2">
        <Label for="tg-pw">{{ t('tg.twoFactorPassword') }}</Label>
        <Input id="tg-pw" v-model="password" type="password" autocomplete="off" @keyup.enter="submitPassword" />
        <p class="text-xs text-muted-foreground">{{ t('tg.twoFactorHint') }}</p>
      </div>
      <div class="flex gap-2">
        <Button class="flex-1" :disabled="busy" @click="submitPassword">
          <Loader2 v-if="busy" class="animate-spin" />
          {{ busy ? t('tg.verifying') : t('tg.completeLink') }}
        </Button>
        <Button variant="outline" @click="cancel">{{ t('common.cancel') }}</Button>
      </div>
    </div>
  </div>
</template>
