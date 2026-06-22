/**
 * Locale controller. The persisted choice (en | ru) lives in localStorage; a
 * pre-paint inline script (see nuxt.config) sets <html lang> before hydration.
 * This composable mirrors the useTheme pattern: shared module-level state plus a
 * `t()` lookup that re-renders templates when the locale changes.
 *
 * `t` is also exported standalone so non-component code (e.g. Pinia stores) can
 * translate strings without instantiating the composable.
 */
import { messages, LOCALES, type Locale, type MsgParams } from '~/i18n/messages'

const STORAGE_KEY = 'hangar-locale'

function initial(): Locale {
  if (!import.meta.client) return 'en'
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'en' || saved === 'ru') return saved
  // First visit: honour a Russian browser, otherwise default to English.
  return (navigator.language || '').toLowerCase().startsWith('ru') ? 'ru' : 'en'
}

// Shared across the app; reading `.value` inside t() makes templates reactive.
const locale = ref<Locale>(initial())

function applyLang(l: Locale) {
  if (import.meta.client) document.documentElement.setAttribute('lang', l)
}

/** Translate `key` for the active locale, interpolating `{name}` placeholders. */
export function t(key: string, params?: MsgParams): string {
  const msg = messages[locale.value]?.[key] ?? messages.en[key]
  if (msg === undefined) return key
  if (typeof msg === 'function') return msg(params ?? {})
  if (!params) return msg
  return msg.replace(/\{(\w+)\}/g, (_, k) => String(params[k] ?? `{${k}}`))
}

export function useI18n() {
  function setLocale(next: Locale) {
    locale.value = next
    if (import.meta.client) localStorage.setItem(STORAGE_KEY, next)
    applyLang(next)
  }

  return { locale, locales: LOCALES, t, setLocale }
}
