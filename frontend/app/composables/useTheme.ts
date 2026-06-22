/**
 * Theme controller. The persisted choice is one of dark | light | system; the
 * effective class on <html> is always dark or light. A pre-paint inline script
 * (see nuxt.config) applies the stored choice before hydration, so this
 * composable only reconciles reactive state and reacts to user toggles.
 */
export type ThemePref = 'dark' | 'light' | 'system'

const STORAGE_KEY = 'hangar-theme'

function systemPrefersDark(): boolean {
  return import.meta.client && window.matchMedia('(prefers-color-scheme: dark)').matches
}

function apply(pref: ThemePref) {
  if (!import.meta.client) return
  const dark = pref === 'dark' || (pref === 'system' && systemPrefersDark())
  const cl = document.documentElement.classList
  cl.toggle('dark', dark)
  cl.toggle('light', !dark)
}

export function useTheme() {
  // Shared across the app for one render tree.
  const pref = useState<ThemePref>('theme-pref', () => {
    if (!import.meta.client) return 'dark'
    return (localStorage.getItem(STORAGE_KEY) as ThemePref) || 'dark'
  })

  const isDark = computed(() =>
    pref.value === 'dark' || (pref.value === 'system' && systemPrefersDark()),
  )

  function set(next: ThemePref) {
    pref.value = next
    if (import.meta.client) localStorage.setItem(STORAGE_KEY, next)
    apply(next)
  }

  /** Cycle between explicit dark and light (ignores system on manual toggle). */
  function toggle() {
    set(isDark.value ? 'light' : 'dark')
  }

  return { pref, isDark, set, toggle }
}
