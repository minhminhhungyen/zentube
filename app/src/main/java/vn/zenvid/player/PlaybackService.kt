package vn.zenvid.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import vn.zenvid.MainActivity

/** Dịch vụ phát nền: hiện điều khiển trên thông báo / màn hình khoá, nghe tiếp khi tắt màn hình. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, PlayerManager.player)
            .setSessionActivity(open)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Vuốt tắt app khỏi đa nhiệm thì dừng phát
        PlayerManager.close()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release() // không giải phóng trình phát dùng chung
        session = null
        super.onDestroy()
    }
}
