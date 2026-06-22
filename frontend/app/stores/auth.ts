import { defineStore } from 'pinia'
import type { User } from '~/types/api'

/**
 * The auth store holds the current user (resolved from the session cookie) and
 * exposes login/setup/logout actions plus a fetchMe used by the global
 * middleware to decide routing.
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as User | null,
    // resolved becomes true once we have attempted a fetchMe at least once.
    resolved: false,
    // Whether the org-wide Telegram account is linked. null = not yet checked;
    // the routing guard resolves it once and forces the admin through the
    // mandatory Telegram onboarding step while it is false.
    telegramLinked: null as boolean | null,
  }),
  getters: {
    isAuthenticated: (s) => s.user !== null,
    isAdmin: (s) => s.user?.role === 'admin',
  },
  actions: {
    /** Resolves the current user from the session cookie; null if unauthenticated. */
    async fetchMe(): Promise<User | null> {
      const api = useApi()
      try {
        const { user } = await api.me()
        this.user = user
      } catch {
        this.user = null
      } finally {
        this.resolved = true
      }
      return this.user
    },

    async login(username: string, password: string): Promise<User> {
      const api = useApi()
      const { user } = await api.login(username, password)
      this.user = user
      this.resolved = true
      // Force the routing guard to re-resolve Telegram status for this session.
      this.telegramLinked = null
      return user
    },

    async setup(username: string, password: string): Promise<User> {
      const api = useApi()
      const { user } = await api.setup(username, password)
      this.user = user
      this.resolved = true
      // A freshly created instance has no Telegram account linked yet.
      this.telegramLinked = false
      return user
    },

    async logout(): Promise<void> {
      const api = useApi()
      try {
        await api.logout()
      } finally {
        this.user = null
        this.telegramLinked = null
      }
    },
  },
})
