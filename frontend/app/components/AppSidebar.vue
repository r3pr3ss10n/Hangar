<script setup lang="ts">
import { HardDrive, UsersRound, Link2, Users, Send, Settings, Database, Info } from 'lucide-vue-next'
import { useAuthStore } from '~/stores/auth'
import { useSidebar } from '~/composables/useSidebar'
import { useI18n } from '~/composables/useI18n'
import { useLabels, tagDotClass } from '~/composables/useLabels'
import { formatSize } from '~/lib/format'

const auth = useAuthStore()
const route = useRoute()
const { mobileOpen, close } = useSidebar()
const { t } = useI18n()
const labels = useLabels()

const nav = [
  { to: '/', labelKey: 'nav.myDrive', icon: HardDrive, exact: true },
  { to: '/shared', labelKey: 'nav.shared', icon: UsersRound, exact: false },
  { to: '/links', labelKey: 'nav.myLinks', icon: Link2, exact: false },
  { to: '/settings', labelKey: 'nav.settings', icon: Settings, exact: false },
  { to: '/about', labelKey: 'nav.about', icon: Info, exact: false },
]
const adminNav = [
  { to: '/admin/users', labelKey: 'nav.users', icon: Users, exact: false },
  { to: '/admin/telegram', labelKey: 'nav.telegram', icon: Send, exact: false },
]

function isActive(to: string, exact: boolean) {
  return exact ? route.path === to : route.path.startsWith(to)
}

// Informational storage-used footer (no quota — storage is effectively unlimited).
const usedBytes = ref<number | null>(null)
onMounted(async () => {
  labels.load()
  try {
    usedBytes.value = (await useApi().storage()).used_bytes
  } catch {
    /* non-fatal: leave the footer hidden */
  }
})

// Close the mobile drawer whenever the route changes.
watch(() => route.fullPath, () => close())
</script>

<template>
  <!-- Mobile backdrop -->
  <Transition
    enter-active-class="transition-opacity duration-200"
    enter-from-class="opacity-0"
    leave-active-class="transition-opacity duration-200"
    leave-to-class="opacity-0"
  >
    <div
      v-if="mobileOpen"
      class="fixed inset-0 z-40 bg-black/50 backdrop-blur-sm md:hidden"
      @click="close"
    />
  </Transition>

  <aside
    class="fixed inset-y-0 left-0 z-50 flex w-60 flex-col border-r bg-card/40 transition-transform duration-200 md:translate-x-0"
    :class="mobileOpen ? 'translate-x-0' : '-translate-x-full'"
  >
    <!-- Brand -->
    <div class="flex h-14 items-center gap-2.5 px-5">
      <div class="flex h-7 w-7 items-center justify-center rounded-md bg-primary text-primary-foreground">
        <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 3 3 8.5v7L12 21l9-5.5v-7L12 3Z" />
          <path d="M12 12 3 8.5M12 12l9-3.5M12 12v9" />
        </svg>
      </div>
      <span class="text-[15px] font-semibold tracking-tight">Hangar</span>
    </div>

    <!-- Nav -->
    <nav class="flex-1 space-y-1 overflow-y-auto px-3 py-2">
      <NuxtLink
        v-for="item in nav"
        :key="item.to"
        :to="item.to"
        class="group flex items-center gap-3 rounded-md px-2.5 py-2 text-sm font-medium transition-colors"
        :class="isActive(item.to, item.exact)
          ? 'bg-accent text-accent-foreground'
          : 'text-muted-foreground hover:bg-accent/50 hover:text-foreground'"
      >
        <component :is="item.icon" class="h-4 w-4 shrink-0" />
        {{ t(item.labelKey) }}
      </NuxtLink>

      <!-- Tags -->
      <template v-if="labels.tags.value.length">
        <div class="px-2.5 pb-1 pt-4 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground/70">
          {{ t('nav.tags') }}
        </div>
        <NuxtLink
          v-for="tag in labels.tags.value"
          :key="tag.id"
          :to="`/tags/${tag.id}`"
          class="group flex items-center gap-3 rounded-md px-2.5 py-2 text-sm font-medium transition-colors"
          :class="route.path === `/tags/${tag.id}`
            ? 'bg-accent text-accent-foreground'
            : 'text-muted-foreground hover:bg-accent/50 hover:text-foreground'"
        >
          <span class="h-2.5 w-2.5 shrink-0 rounded-full" :class="tagDotClass(tag.color)" />
          <span class="min-w-0 flex-1 truncate">{{ tag.name }}</span>
          <span v-if="tag.item_count" class="shrink-0 text-xs text-muted-foreground/60">{{ tag.item_count }}</span>
        </NuxtLink>
      </template>

      <template v-if="auth.isAdmin">
        <div class="px-2.5 pb-1 pt-4 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground/70">
          {{ t('nav.admin') }}
        </div>
        <NuxtLink
          v-for="item in adminNav"
          :key="item.to"
          :to="item.to"
          class="group flex items-center gap-3 rounded-md px-2.5 py-2 text-sm font-medium transition-colors"
          :class="isActive(item.to, item.exact)
            ? 'bg-accent text-accent-foreground'
            : 'text-muted-foreground hover:bg-accent/50 hover:text-foreground'"
        >
          <component :is="item.icon" class="h-4 w-4 shrink-0" />
          {{ t(item.labelKey) }}
        </NuxtLink>
      </template>
    </nav>

    <!-- Storage used (informational, no quota) -->
    <div
      v-if="usedBytes !== null"
      class="flex items-center gap-2 px-4 py-3 text-xs text-muted-foreground/70"
    >
      <Database class="h-3.5 w-3.5 shrink-0" />
      <span class="truncate">{{ t('sidebar.storageUsed', { size: formatSize(usedBytes) }) }}</span>
    </div>
  </aside>
</template>
