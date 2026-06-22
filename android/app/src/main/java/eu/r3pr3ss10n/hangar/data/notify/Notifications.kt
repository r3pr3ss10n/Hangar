package eu.r3pr3ss10n.hangar.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import eu.r3pr3ss10n.hangar.R

/** Notification channel ids and one-time channel registration. */
object Notifications {
    const val CHANNEL_UPLOADS = "uploads"
    const val CHANNEL_DOWNLOADS = "downloads"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPLOADS,
                context.getString(R.string.notify_channel_uploads_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notify_channel_uploads_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                context.getString(R.string.notify_channel_downloads_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notify_channel_downloads_desc) },
        )
    }
}
