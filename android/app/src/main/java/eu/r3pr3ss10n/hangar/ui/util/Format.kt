package eu.r3pr3ss10n.hangar.ui.util

import java.util.Locale

/** Formats a byte count as a human-readable size (KB/MB/GB), matching the web. */
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return String.format(Locale.US, "%.1f %s", value, units[i])
}

/**
 * Formats an RFC3339 timestamp (as the backend emits) into a short, relative-ish
 * label. Kept dependency-free: parses the date portion and shows it plainly,
 * falling back to the raw string if parsing fails.
 */
fun formatDate(rfc3339: String): String {
    // e.g. "2026-06-22T17:08:00Z" -> "2026-06-22"
    val datePart = rfc3339.substringBefore('T')
    return datePart.ifEmpty { rfc3339 }
}
