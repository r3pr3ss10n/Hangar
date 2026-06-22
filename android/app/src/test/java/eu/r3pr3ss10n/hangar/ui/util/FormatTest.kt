package eu.r3pr3ss10n.hangar.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun bytesUnderKilobyte() {
        assertEquals("512 B", formatSize(512))
    }

    @Test
    fun kilobytes() {
        assertEquals("1.0 KB", formatSize(1024))
    }

    @Test
    fun megabytes() {
        assertEquals("1.5 MB", formatSize((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun gigabytes() {
        assertEquals("2.0 GB", formatSize(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun dateTakesDatePortion() {
        assertEquals("2026-06-22", formatDate("2026-06-22T17:08:00Z"))
    }
}
