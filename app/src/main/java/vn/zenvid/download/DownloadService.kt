package vn.zenvid.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.zenvid.MainActivity
import vn.zenvid.data.DownloadEntry

/** Dịch vụ chạy nền khi đang tải: giữ cho việc tải không bị dừng khi tắt màn hình hoặc rời app. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val nm by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, progressNotification("Đang chuẩn bị tải…", 0, 0),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (job?.isActive != true) {
            job = scope.launch {
                Downloads.runQueue(
                    onProgress = { title, done, total -> nm.notify(NOTIF_ID, progressNotification(title, done, total)) },
                    onFinished = ::finishedNotification,
                )
                withContext(Dispatchers.Main) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 1,
        Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(title: String, done: Long, total: Long): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (total > 0) "${mb(done)} / ${mb(total)}" else "Đang tải…")
            .setProgress(100, if (total > 0) (done * 100 / total).toInt() else 0, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .build()

    private fun finishedNotification(d: DownloadEntry, ok: Boolean) {
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (ok) "Đã tải xong" else "Tải không thành công")
            .setContentText(d.title + if (!ok && d.error != null) " – ${d.error}" else "")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        nm.notify((d.id % 100000).toInt() + 1000, n)
    }

    private fun createChannel() {
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Tải xuống", NotificationManager.IMPORTANCE_LOW))
    }

    private fun mb(b: Long) = String.format(java.util.Locale.US, "%.1f MB", b / 1_048_576.0)

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 42
    }
}
