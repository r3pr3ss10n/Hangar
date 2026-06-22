import { reactive } from 'vue'

/**
 * Minimal toast store. A single module-level queue is shared across the app
 * (SPA-only), rendered by <Toaster>. Toasts auto-dismiss unless duration <= 0.
 */
export type ToastVariant = 'default' | 'success' | 'error'

export interface ToastItem {
  id: number
  title: string
  description?: string
  variant: ToastVariant
  duration: number
}

const toasts = reactive<ToastItem[]>([])
let seq = 0

function dismiss(id: number) {
  const i = toasts.findIndex((t) => t.id === id)
  if (i !== -1) toasts.splice(i, 1)
}

function push(
  title: string,
  opts: { description?: string; variant?: ToastVariant; duration?: number } = {},
) {
  const id = ++seq
  const duration = opts.duration ?? 4000
  toasts.push({
    id,
    title,
    description: opts.description,
    variant: opts.variant ?? 'default',
    duration,
  })
  if (duration > 0 && import.meta.client) {
    setTimeout(() => dismiss(id), duration)
  }
  return id
}

export function useToast() {
  return {
    toasts,
    dismiss,
    toast: push,
    success: (title: string, description?: string) => push(title, { description, variant: 'success' }),
    error: (title: string, description?: string) => push(title, { description, variant: 'error' }),
  }
}
