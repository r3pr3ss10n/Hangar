// In-app preview is image-only by design: images are the one type worth showing
// inline (and we already generate thumbnails for them). Everything else
// downloads directly — rendering video/PDF/etc. in-app earns little and streams
// the whole file from Telegram just to look at it.

/** isPreviewable reports whether a file can be previewed in-app (images only). */
export function isPreviewable(file: { mime?: string; name: string }): boolean {
  return (file.mime ?? '').toLowerCase().startsWith('image/')
}
