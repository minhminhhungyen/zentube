package vn.zenvid

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import vn.zenvid.player.PlaybackService
import vn.zenvid.player.PlayerManager
import vn.zenvid.ui.AppRoot
import vn.zenvid.ui.theme.ZenvidTheme

class MainActivity : ComponentActivity() {

    /** Liên kết YouTube cần mở (từ trình duyệt, nút chia sẻ, hoặc thông báo trình phát). */
    val pendingLink = MutableStateFlow<String?>(null)
    val inPip = mutableStateOf(false)

    /** Màn hình video đang hiển thị – dùng để quyết định có vào chế độ PiP hay không. */
    var onVideoScreen = false

    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        vn.zenvid.download.Downloads.resumePending()

        // Kết nối tới dịch vụ phát nền để có điều khiển trên thông báo / màn hình khoá
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)
        PlayerManager.player.addListener(pipPlayerListener)
        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()

        setContent {
            val theme by Services.prefs.theme.flow.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (theme) { "dark" -> true; "light" -> false; else -> systemDark }
            // Màu biểu tượng thanh trạng thái theo giao diện đã chọn trong app
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ZenvidTheme(theme) { AppRoot(this) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val link = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.takeUnless { it.startsWith("zenvid://") }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                Regex("""https?://\S+""").find(text)?.value
            }
            ACTION_OPEN_PLAYER -> OPEN_PLAYER
            ACTION_OPEN_DOWNLOADS -> OPEN_DOWNLOADS
            else -> null
        }
        if (link != null) pendingLink.value = link
    }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || Services.prefs.askedNotifications.value) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Services.prefs.askedNotifications.value = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    // ---------------------------------------------------------------- cửa sổ nổi (PiP)
    /** Nút trên cửa sổ nổi: phát/dừng và "Nghe ẩn" (tắt cửa sổ, nhạc vẫn chạy). */
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val player = PlayerManager.player
            when (intent.getIntExtra(EXTRA_PIP_ACTION, 0)) {
                PIP_PLAY_PAUSE -> if (player.playWhenReady) player.pause() else player.play()
                PIP_LISTEN -> {
                    PlayerManager.setAudioOnly(true)
                    player.play()
                    moveTaskToBack(true) // đóng cửa sổ nổi, âm thanh tiếp tục ở chế độ Nghe ẩn
                }
            }
            updatePip()
        }
    }

    private fun pipAction(code: Int, icon: Int, title: String): RemoteAction {
        val pi = PendingIntent.getBroadcast(
            this, code,
            Intent(ACTION_PIP).setPackage(packageName).putExtra(EXTRA_PIP_ACTION, code),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(this, icon), title, title, pi)
    }

    private fun pipParams(): PictureInPictureParams {
        val playing = PlayerManager.player.playWhenReady
        val actions = listOf(
            pipAction(PIP_PLAY_PAUSE, if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play, if (playing) "Tạm dừng" else "Phát"),
            pipAction(PIP_LISTEN, R.drawable.ic_pip_headphones, "Nghe ẩn"),
        ).take(runCatching { maxNumPictureInPictureActions }.getOrDefault(2).coerceAtLeast(1))
        // Cửa sổ nổi theo đúng tỉ lệ video (video dọc thì cửa sổ dọc); Android giới hạn trong khoảng 1:2.39 – 2.39:1
        val a = PlayerManager.videoAspect.value.takeIf { it > 0f }?.coerceIn(0.42f, 2.39f) ?: (16f / 9f)
        return PictureInPictureParams.Builder().setAspectRatio(Rational((a * 1000).toInt(), 1000)).setActions(actions).build()
    }

    private fun updatePip() {
        if (isInPictureInPictureMode) runCatching { setPictureInPictureParams(pipParams()) }
    }

    private val pipPlayerListener = object : androidx.media3.common.Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updatePip()
    }

    fun enterPip(): Boolean {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return runCatching { enterPictureInPictureMode(pipParams()) }.getOrDefault(false)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Bấm nút Home khi đang xem video -> thu nhỏ thành cửa sổ nổi (PiP)
        if (onVideoScreen && PlayerManager.player.isPlaying && !PlayerManager.audioOnly.value) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        // Đóng cửa sổ nổi (bấm X) thì dừng phát, giống YouTube – trừ khi bấm "Nghe ẩn"
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !PlayerManager.audioOnly.value) {
            PlayerManager.player.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        // Tắt "phát nền" trong Cài đặt thì dừng khi rời app
        // Nghe ẩn thì luôn phát tiếp khi rời app
        if (!isChangingConfigurations && !Services.prefs.backgroundPlay.value && !PlayerManager.audioOnly.value) PlayerManager.player.pause()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        PlayerManager.player.removeListener(pipPlayerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "vn.zenvid.OPEN_PLAYER"
        private const val ACTION_PIP = "vn.zenvid.PIP_ACTION"
        private const val EXTRA_PIP_ACTION = "action"
        private const val PIP_PLAY_PAUSE = 1
        private const val PIP_LISTEN = 2
        const val OPEN_PLAYER = "zenvid://player"
        const val ACTION_OPEN_DOWNLOADS = "vn.zenvid.OPEN_DOWNLOADS"
        const val OPEN_DOWNLOADS = "zenvid://downloads"
    }
}
