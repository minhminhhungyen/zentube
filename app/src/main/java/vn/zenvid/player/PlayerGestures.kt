package vn.zenvid.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/** Thông báo nhỏ hiện giữa trình phát khi đang thao tác (vd. "Âm lượng 60%"). */
data class GestureHint(val kind: Kind, val text: String, val progress: Float? = null) {
    enum class Kind { SEEK_BACK, SEEK_FORWARD, SCRUB, VOLUME, BRIGHTNESS, SPEED, ZOOM }
}

/**
 * Cử chỉ trên trình phát (giống YouTube / MX Player):
 *  - Chạm 1 lần: hiện/ẩn nút điều khiển
 *  - Chạm 2 lần bên trái / phải: lùi / tới 10 giây (chạm tiếp để cộng dồn)
 *  - Giữ ngón tay: phát nhanh x2, thả ra trở lại bình thường
 *  - Vuốt ngang: tua tới / lui (thả tay mới tua)
 *  - Toàn màn hình: vuốt dọc nửa trái = độ sáng, nửa phải = âm lượng; chụm/xoè 2 ngón = vừa khung / lấp đầy
 *  - Khung nhỏ: vuốt lên = toàn màn hình, vuốt xuống = thu nhỏ trình phát
 */
@OptIn(UnstableApi::class)
class PlayerGestures(
    private val context: Context,
    private val view: PlayerView,
    private val isFullscreen: () -> Boolean,
    private val onHint: (GestureHint?) -> Unit,
    private val onSwipeUp: () -> Unit,
    private val onSwipeDown: () -> Unit,
    private val onZoom: (Boolean) -> Unit,
) {
    private val player get() = PlayerManager.player
    private val audio = context.getSystemService(AudioManager::class.java)
    private val density = context.resources.displayMetrics.density

    private enum class Mode { NONE, SCRUB, VOLUME, BRIGHTNESS, SWIPE }
    private var mode = Mode.NONE
    private var scrubStartPos = 0L
    private var scrubTarget = 0L
    private var startVolume = 0f
    private var startBrightness = 0f
    private var swipeDy = 0f
    private var boosted = false
    private var savedSpeed = 1f
    private var seekAccum = 0
    private var lastSeekAt = 0L
    private var scale = 1f
    private var scaling = false

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            mode = Mode.NONE
            swipeDy = 0f
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (view.isControllerFullyVisible) view.hideController() else view.showController()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val third = view.width / 3f
            when {
                e.x < third -> seekBy(-10)
                e.x > third * 2 -> seekBy(10)
                else -> if (player.playWhenReady) player.pause() else player.play() // giữa: phát / dừng
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (scaling || !player.isPlaying) return
            boosted = true
            savedSpeed = player.playbackParameters.speed
            player.playbackParameters = PlaybackParameters(2f)
            onHint(GestureHint(GestureHint.Kind.SPEED, "Tốc độ 2x"))
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scaling || boosted || e1 == null) return false
            val totalX = e2.x - e1.x
            val totalY = e2.y - e1.y
            if (mode == Mode.NONE) {
                mode = when {
                    abs(totalX) > abs(totalY) -> Mode.SCRUB.also { scrubStartPos = player.currentPosition; scrubTarget = scrubStartPos }
                    !isFullscreen() -> Mode.SWIPE
                    e1.x < view.width / 2f -> Mode.BRIGHTNESS.also { startBrightness = currentBrightness() }
                    else -> Mode.VOLUME.also { startVolume = currentVolume() }
                }
                view.hideController()
            }
            val h = view.height.coerceAtLeast(1).toFloat()
            when (mode) {
                Mode.SCRUB -> {
                    // Vuốt hết chiều ngang = 90 giây (video ngắn thì tối đa bằng độ dài video)
                    val dur = player.duration.takeIf { it > 0 } ?: return true
                    val range = minOf(90_000L, dur)
                    scrubTarget = (scrubStartPos + (totalX / view.width) * range).toLong().coerceIn(0, dur)
                    val delta = (scrubTarget - scrubStartPos) / 1000
                    onHint(
                        GestureHint(
                            GestureHint.Kind.SCRUB,
                            "${fmt(scrubTarget)} / ${fmt(dur)}  (${if (delta >= 0) "+" else ""}${delta} giây)",
                            scrubTarget.toFloat() / dur,
                        )
                    )
                }
                Mode.VOLUME -> {
                    val v = (startVolume - totalY / (h * 0.8f)).coerceIn(0f, 1f)
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).toInt(), 0)
                    onHint(GestureHint(GestureHint.Kind.VOLUME, "Âm lượng ${(v * 100).toInt()}%", v))
                }
                Mode.BRIGHTNESS -> {
                    val b = (startBrightness - totalY / (h * 0.8f)).coerceIn(0.01f, 1f)
                    setBrightness(b)
                    onHint(GestureHint(GestureHint.Kind.BRIGHTNESS, "Độ sáng ${(b * 100).toInt()}%", b))
                }
                Mode.SWIPE -> swipeDy = totalY
                Mode.NONE -> {}
            }
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (!isFullscreen()) return false
            scaling = true
            scale = 1f
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scale *= detector.scaleFactor
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            when {
                scale > 1.1f -> { onZoom(true); onHint(GestureHint(GestureHint.Kind.ZOOM, "Lấp đầy màn hình")) }
                scale < 0.9f -> { onZoom(false); onHint(GestureHint(GestureHint.Kind.ZOOM, "Vừa khung")) }
            }
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        view.setOnTouchListener { _, ev ->
            scaleDetector.onTouchEvent(ev)
            detector.onTouchEvent(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) finish()
            true
        }
    }

    private fun finish() {
        when (mode) {
            Mode.SCRUB -> if (scrubTarget != scrubStartPos) player.seekTo(scrubTarget)
            Mode.SWIPE -> when {
                swipeDy < -60 * density -> onSwipeUp()
                swipeDy > 60 * density -> onSwipeDown()
            }
            else -> {}
        }
        if (boosted) {
            boosted = false
            player.playbackParameters = PlaybackParameters(savedSpeed)
            onHint(null)
        }
        mode = Mode.NONE
        scaling = false
    }

    private fun seekBy(sec: Int) {
        val now = System.currentTimeMillis()
        // Chạm 2 lần liên tiếp cùng phía thì cộng dồn: 10, 20, 30 giây…
        seekAccum = if (now - lastSeekAt < 1200 && (seekAccum > 0) == (sec > 0)) seekAccum + sec else sec
        lastSeekAt = now
        val dur = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + sec * 1000L).coerceIn(0, dur))
        onHint(
            GestureHint(
                if (sec < 0) GestureHint.Kind.SEEK_BACK else GestureHint.Kind.SEEK_FORWARD,
                "${abs(seekAccum)} giây",
            )
        )
    }

    private fun currentVolume(): Float =
        audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    private fun currentBrightness(): Float {
        val w = (context as? Activity)?.window ?: return 0.5f
        val b = w.attributes.screenBrightness
        if (b in 0f..1f) return b
        return runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
    }

    private fun setBrightness(b: Float) {
        val w = (context as? Activity)?.window ?: return
        w.attributes = w.attributes.apply { screenBrightness = b }
    }

    companion object {
        /** Trả độ sáng về theo cài đặt hệ thống (khi rời màn hình video). */
        fun resetBrightness(activity: Activity) {
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }

        fun fmt(ms: Long): String {
            val s = ms / 1000
            return if (s >= 3600) String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
            else String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
        }
    }
}
