import { ref } from 'vue'

// Mobile drawer open state, shared between the topbar toggle and the sidebar.
const mobileOpen = ref(false)

export function useSidebar() {
  return {
    mobileOpen,
    open: () => (mobileOpen.value = true),
    close: () => (mobileOpen.value = false),
    toggle: () => (mobileOpen.value = !mobileOpen.value),
  }
}
