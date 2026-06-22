import { ref } from 'vue'

// Module-level so the topbar trigger, the ⌘K shortcut, and the palette itself
// share one open state.
const open = ref(false)

export function useCommandPalette() {
  return {
    open,
    show: () => (open.value = true),
    hide: () => (open.value = false),
    toggle: () => (open.value = !open.value),
  }
}
