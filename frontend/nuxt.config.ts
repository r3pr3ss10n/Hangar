// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2024-11-01',
  ssr: false,
  devtools: { enabled: false },
  modules: ['@pinia/nuxt', '@nuxtjs/tailwindcss'],
  css: ['~/assets/css/main.css'],

  // main.css already carries the @tailwind directives, so disable the module's
  // own injected stylesheet to avoid duplicating base/utilities.
  tailwindcss: { cssPath: false },

  // App-level components auto-import without a path prefix; the shadcn-style
  // primitives under components/ui are imported explicitly (barrel files), so
  // they are excluded from auto-import to avoid duplicate registration.
  components: [{ path: '~/components', pathPrefix: false, ignore: ['**/ui/**'] }],

  runtimeConfig: {
    public: {
      // Overridden at runtime by NUXT_PUBLIC_API_BASE.
      apiBase: 'http://localhost:8080',
    },
  },

  app: {
    head: {
      title: 'Hangar',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'color-scheme', content: 'dark light' },
        { name: 'theme-color', content: '#121214' },
      ],
      link: [
        { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' },
        { rel: 'apple-touch-icon', href: '/favicon.svg' },
      ],
      script: [
        {
          // Apply the persisted theme before first paint to avoid a flash.
          innerHTML:
            "(function(){try{var t=localStorage.getItem('hangar-theme')||'dark';var d=t==='dark'||(t==='system'&&matchMedia('(prefers-color-scheme: dark)').matches);var c=document.documentElement.classList;c.add(d?'dark':'light');c.remove(d?'light':'dark');}catch(e){document.documentElement.classList.add('dark');}})();",
          tagPosition: 'head',
        },
        {
          // Reflect the persisted locale on <html lang> before hydration.
          innerHTML:
            "(function(){try{var l=localStorage.getItem('hangar-locale');if(l!=='en'&&l!=='ru'){l=(navigator.language||'').toLowerCase().indexOf('ru')===0?'ru':'en';}document.documentElement.setAttribute('lang',l);}catch(e){document.documentElement.setAttribute('lang','en');}})();",
          tagPosition: 'head',
        },
      ],
    },
  },
})
