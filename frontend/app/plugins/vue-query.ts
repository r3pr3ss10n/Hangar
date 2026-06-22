import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'

/**
 * Registers @tanstack/vue-query with sensible SPA defaults. Since the app is
 * client-only (ssr:false) we do not need hydration plumbing.
 */
export default defineNuxtPlugin((nuxtApp) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: 1,
        staleTime: 5_000,
        refetchOnWindowFocus: false,
      },
    },
  })
  nuxtApp.vueApp.use(VueQueryPlugin, { queryClient })
})
