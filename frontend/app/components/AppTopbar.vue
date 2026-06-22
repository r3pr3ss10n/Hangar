<script setup lang="ts">
import { PanelLeft, Search, ChevronRight, LogOut, ShieldCheck } from 'lucide-vue-next'
import { useAuthStore } from '~/stores/auth'
import { useDriveStore } from '~/stores/drive'
import { useSidebar } from '~/composables/useSidebar'
import { useCommandPalette } from '~/composables/useCommandPalette'
import { useI18n } from '~/composables/useI18n'
import { Button } from '~/components/ui/button'
import { Avatar } from '~/components/ui/avatar'
import { Badge } from '~/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
} from '~/components/ui/dropdown-menu'

const auth = useAuthStore()
const drive = useDriveStore()
const route = useRoute()
const router = useRouter()
const { toggle: toggleSidebar } = useSidebar()
const { show: showPalette } = useCommandPalette()
const { t } = useI18n()

const isDrive = computed(() => route.path === '/')

const pageTitle = computed(() => {
  if (route.path.startsWith('/admin/users')) return t('nav.users')
  if (route.path.startsWith('/admin/telegram')) return t('nav.telegram')
  if (route.path.startsWith('/settings')) return t('nav.settings')
  return 'Hangar'
})

async function onLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <header
    class="sticky top-0 z-30 flex h-14 items-center gap-3 border-b bg-background/80 px-4 backdrop-blur-md"
  >
    <Button
      variant="ghost"
      size="icon-sm"
      class="md:hidden"
      :aria-label="t('topbar.toggleMenu')"
      @click="toggleSidebar"
    >
      <PanelLeft />
    </Button>

    <!-- Breadcrumbs (drive) or page title -->
    <div class="flex min-w-0 flex-1 items-center">
      <nav v-if="isDrive" class="flex items-center gap-0.5 overflow-x-auto text-sm">
        <template v-for="(c, i) in drive.crumbs" :key="c.id ?? 'root'">
          <button
            class="shrink-0 rounded-md px-2 py-1 transition-colors hover:bg-accent"
            :class="i === drive.crumbs.length - 1
              ? 'font-medium text-foreground'
              : 'text-muted-foreground hover:text-foreground'"
            @click="drive.goToCrumb(i)"
          >
            {{ c.name }}
          </button>
          <ChevronRight
            v-if="i < drive.crumbs.length - 1"
            class="h-3.5 w-3.5 shrink-0 text-muted-foreground/50"
          />
        </template>
      </nav>
      <h1 v-else class="truncate text-sm font-medium">{{ pageTitle }}</h1>
    </div>

    <!-- Command palette trigger -->
    <button
      class="hidden h-9 items-center gap-2 rounded-md border bg-card/60 px-3 text-sm text-muted-foreground transition-colors hover:bg-accent sm:flex"
      @click="showPalette"
    >
      <Search class="h-4 w-4" />
      <span>{{ t('topbar.search') }}</span>
      <kbd class="ml-2 rounded border bg-muted px-1.5 font-mono text-[10px] leading-5 text-muted-foreground">⌘K</kbd>
    </button>
    <Button variant="ghost" size="icon-sm" class="sm:hidden" :aria-label="t('topbar.searchAria')" @click="showPalette">
      <Search />
    </Button>

    <ThemeToggle />

    <!-- User menu -->
    <DropdownMenu>
      <DropdownMenuTrigger as-child>
        <button class="rounded-full outline-none ring-ring ring-offset-2 ring-offset-background focus-visible:ring-2">
          <Avatar :name="auth.user?.username" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" class="w-56">
        <DropdownMenuLabel>
          <div class="flex flex-col gap-1">
            <span class="text-sm font-medium text-foreground">{{ auth.user?.username }}</span>
            <Badge v-if="auth.isAdmin" variant="muted" class="w-fit">
              <ShieldCheck class="h-3 w-3" /> {{ t('role.admin') }}
            </Badge>
            <span v-else class="text-xs text-muted-foreground">{{ t('role.user') }}</span>
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" @select="onLogout">
          <LogOut /> {{ t('topbar.logout') }}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  </header>
</template>
