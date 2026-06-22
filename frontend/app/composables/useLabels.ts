/**
 * Labels controller: the user's favourites (starred files/folders) and tags.
 * State is module-level so the drive, sidebar, and the favourites/tag pages all
 * share one source of truth. The whole bundle is loaded once via /api/labels and
 * updated optimistically on every toggle.
 */
import { reactive, ref } from 'vue'
import type { Tag } from '~/types/api'

export type ItemKind = 'file' | 'folder'

// Tag colour palette. Class strings are literal so Tailwind compiles them.
export const TAG_COLORS = [
  'slate',
  'red',
  'orange',
  'amber',
  'green',
  'teal',
  'blue',
  'violet',
  'pink',
] as const

const DOT: Record<string, string> = {
  slate: 'bg-slate-500',
  red: 'bg-red-500',
  orange: 'bg-orange-500',
  amber: 'bg-amber-500',
  green: 'bg-green-500',
  teal: 'bg-teal-500',
  blue: 'bg-blue-500',
  violet: 'bg-violet-500',
  pink: 'bg-pink-500',
}
const BADGE: Record<string, string> = {
  slate: 'bg-slate-500/15 text-slate-600 dark:text-slate-300',
  red: 'bg-red-500/15 text-red-600 dark:text-red-400',
  orange: 'bg-orange-500/15 text-orange-600 dark:text-orange-400',
  amber: 'bg-amber-500/15 text-amber-600 dark:text-amber-400',
  green: 'bg-green-500/15 text-green-600 dark:text-green-400',
  teal: 'bg-teal-500/15 text-teal-600 dark:text-teal-400',
  blue: 'bg-blue-500/15 text-blue-600 dark:text-blue-400',
  violet: 'bg-violet-500/15 text-violet-600 dark:text-violet-400',
  pink: 'bg-pink-500/15 text-pink-600 dark:text-pink-400',
}
export const tagDotClass = (color: string) => DOT[color] || DOT.slate
export const tagBadgeClass = (color: string) => BADGE[color] || BADGE.slate

// Shared state.
const tags = ref<Tag[]>([])
// resourceId -> tagId[] for files and folders.
const fileTags = reactive(new Map<string, string[]>())
const folderTags = reactive(new Map<string, string[]>())
const loaded = ref(false)

function tagMap(kind: ItemKind) {
  return kind === 'folder' ? folderTags : fileTags
}

export function useLabels() {
  const api = useApi()

  async function load(force = false) {
    if (loaded.value && !force) return
    const data = await api.getLabels()
    tags.value = data.tags
    fileTags.clear()
    for (const [id, ids] of Object.entries(data.file_tags)) fileTags.set(id, ids)
    folderTags.clear()
    for (const [id, ids] of Object.entries(data.folder_tags)) folderTags.set(id, ids)
    loaded.value = true
  }

  /** Resolved tag objects assigned to an item (skips any unknown ids). */
  function tagsFor(kind: ItemKind, id: string): Tag[] {
    const ids = tagMap(kind).get(id)
    if (!ids?.length) return []
    return ids.map((tid) => tags.value.find((t) => t.id === tid)).filter((t): t is Tag => !!t)
  }

  const hasTag = (kind: ItemKind, id: string, tagId: string) =>
    (tagMap(kind).get(id) || []).includes(tagId)

  async function assignTag(kind: ItemKind, id: string, tagId: string, on: boolean) {
    const map = tagMap(kind)
    const cur = map.get(id) || []
    const next = on ? [...new Set([...cur, tagId])] : cur.filter((t) => t !== tagId)
    map.set(id, next)
    try {
      if (kind === 'folder') {
        if (on) await api.addFolderTag(id, tagId)
        else await api.removeFolderTag(id, tagId)
      } else {
        if (on) await api.addFileTag(id, tagId)
        else await api.removeFileTag(id, tagId)
      }
    } catch (err) {
      map.set(id, cur)
      throw err
    }
  }

  async function createTag(name: string, color: string): Promise<Tag> {
    const tag = await api.createTag(name, color)
    tags.value = [...tags.value, { ...tag, item_count: 0 }].sort((a, b) =>
      a.name.localeCompare(b.name),
    )
    return tag
  }

  async function editTag(id: string, name: string, color: string) {
    await api.updateTag(id, name, color)
    tags.value = tags.value
      .map((t) => (t.id === id ? { ...t, name, color } : t))
      .sort((a, b) => a.name.localeCompare(b.name))
  }

  async function removeTag(id: string) {
    await api.deleteTag(id)
    tags.value = tags.value.filter((t) => t.id !== id)
    // Drop the tag from every item's assignment list.
    for (const m of [fileTags, folderTags]) {
      for (const [k, v] of m) {
        if (v.includes(id)) m.set(k, v.filter((t) => t !== id))
      }
    }
  }

  return {
    tags,
    loaded,
    load,
    tagsFor,
    hasTag,
    assignTag,
    createTag,
    editTag,
    removeTag,
  }
}
