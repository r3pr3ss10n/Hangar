package eu.r3pr3ss10n.hangar.data.upload

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import eu.r3pr3ss10n.hangar.R
import eu.r3pr3ss10n.hangar.data.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UploadService keeps the process alive while uploads run, so backgrounding the
 * app (or turning the screen off) does not kill an in-flight transfer. It mirrors
 * the [UploadManager] queue into a foreground progress notification and stops
 * itself once no active work remains.
 */
@AndroidEntryPoint
class UploadService : Service() {

    @Inject
    lateinit var uploadManager: UploadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        startAsForeground(buildNotification(active = 0, done = 0, failed = 0))

        scope.launch {
            uploadManager.items.collectLatest { items ->
                val active = items.count {
                    it.status == UploadStatus.PENDING || it.status == UploadStatus.UPLOADING
                }
                val done = items.count { it.status == UploadStatus.COMPLETED }
                val failed = items.count { it.status == UploadStatus.FAILED }
                if (active == 0) {
                    stopSelf()
                } else {
                    notify(buildNotification(active, done, failed))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(notification: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, notification)
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(active: Int, done: Int, failed: Int): Notification {
        val text = buildString {
            append(resources.getQuantityString(R.plurals.notify_upload_active, active, active))
            if (done > 0) append(" · " + resources.getQuantityString(R.plurals.notify_upload_done, done, done))
            if (failed > 0) append(" · " + resources.getQuantityString(R.plurals.notify_upload_failed, failed, failed))
        }
        return NotificationCompat.Builder(this, Notifications.CHANNEL_UPLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.notify_upload_title))
            .setContentText(text)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 42

        /** Starts the foreground service to back the current upload queue. */
        fun start(context: Context) {
            val intent = Intent(context, UploadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
