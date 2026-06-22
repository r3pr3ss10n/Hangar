import { useAuthStore } from '~/stores/auth'

/**
 * Global routing guard. On first navigation it asks the backend whether setup
 * is required, then resolves the current user. Unauthenticated visitors are
 * funneled to /setup (first run) or /login; authenticated visitors are kept off
 * the public pages. Admin-only pages additionally require the admin role.
 */
export default defineNuxtRouteMiddleware(async (to) => {
  // Middleware runs only on the client (ssr:false); guard anyway.
  if (import.meta.server) return

  // Public share pages (/s/:token) are reachable by anyone, signed in or not,
  // and must not be funnelled to /login or the setup wizard.
  if (to.path.startsWith('/s/')) return

  const auth = useAuthStore()
  const api = useApi()

  const publicRoutes = ['/login', '/setup']

  // Determine whether the instance still needs first-run setup.
  let needsSetup = false
  try {
    const status = await api.setupStatus()
    needsSetup = status.needs_setup
  } catch {
    // If the backend is unreachable we cannot make a routing decision; let the
    // target page render and surface its own error state.
    needsSetup = false
  }

  // Resolve the session once per app load.
  if (!auth.resolved) {
    await auth.fetchMe()
  }

  // First-run: force everyone to the setup wizard.
  if (needsSetup) {
    if (to.path !== '/setup') return navigateTo('/setup')
    return
  }

  // Setup already done: keep users away from the setup wizard.
  if (to.path === '/setup') {
    return navigateTo(auth.isAuthenticated ? '/' : '/login')
  }

  // Unauthenticated: only the login page is reachable.
  if (!auth.isAuthenticated) {
    if (to.path !== '/login') return navigateTo('/login')
    return
  }

  // Authenticated: do not show the login page.
  if (to.path === '/login') {
    return navigateTo('/')
  }

  // Telegram onboarding gate: linking the org-wide Telegram account is the
  // mandatory first step right after the admin account is created — it is the
  // storage backend, so nothing works without it. Only the admin can manage it,
  // so non-admins skip this entirely.
  const onboardingTelegram = '/onboarding/telegram'
  if (auth.isAdmin) {
    if (auth.telegramLinked === null) {
      try {
        const tg = await api.telegramStatus()
        auth.telegramLinked = tg.status === 'linked'
      } catch {
        // Treat an unreachable status check as "linked" so we don't trap the
        // admin on the onboarding page when the backend is merely flaky.
        auth.telegramLinked = true
      }
    }
    if (!auth.telegramLinked) {
      if (to.path !== onboardingTelegram) return navigateTo(onboardingTelegram)
      return
    }
  }

  // Once linked (or for non-admins), the onboarding step is off-limits.
  if (to.path === onboardingTelegram) {
    return navigateTo('/')
  }

  // Admin gate.
  if (to.path.startsWith('/admin') && !auth.isAdmin) {
    return navigateTo('/')
  }
})
