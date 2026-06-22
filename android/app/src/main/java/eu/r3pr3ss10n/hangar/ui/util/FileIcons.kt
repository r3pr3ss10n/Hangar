package eu.r3pr3ss10n.hangar.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps a file's MIME/name to a representative Material icon. */
fun fileIcon(mime: String, name: String): ImageVector {
    val m = mime.lowercase()
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        m.startsWith("image/") -> Icons.Filled.Image
        m.startsWith("video/") -> Icons.Filled.VideoFile
        m.startsWith("audio/") -> Icons.Filled.AudioFile
        m == "application/pdf" || ext == "pdf" -> Icons.Filled.PictureAsPdf
        m.contains("zip") || m.contains("tar") || m.contains("compressed") ||
            ext in setOf("zip", "rar", "7z", "gz", "tar") -> Icons.Filled.Archive
        m.contains("spreadsheet") || ext in setOf("xls", "xlsx", "csv") -> Icons.Filled.TableChart
        m.startsWith("text/") || ext in setOf("txt", "md", "rtf") -> Icons.Filled.Description
        ext in setOf("json", "xml", "html", "js", "ts", "kt", "java", "go", "py", "c", "cpp", "rs", "sh") ->
            Icons.Filled.Code
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

/** The composable color for a tag's palette name, mirroring the web swatches. */
fun tagColorHex(color: String): Long = when (color) {
    "red" -> 0xFFEF4444
    "orange" -> 0xFFF97316
    "amber" -> 0xFFF59E0B
    "green" -> 0xFF22C55E
    "teal" -> 0xFF14B8A6
    "blue" -> 0xFF3B82F6
    "violet" -> 0xFF8B5CF6
    "pink" -> 0xFFEC4899
    else -> 0xFF64748B // slate
}
