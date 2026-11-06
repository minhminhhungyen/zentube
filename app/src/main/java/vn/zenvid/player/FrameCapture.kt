package vn.zenvid.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/** Giữ tham chiếu tới khung hình đang hiển thị để chụp ảnh từ bất kỳ đâu trên màn hình video. */
object PlayerViewRef {
    var view: java.lang.ref.WeakReference<PlayerView>? = null
    fun get(): PlayerView? = view?.get()
}

/**
 * Chụp đúng khung hình video đang phát (độ phân giải gốc của video, không dính nút điều khiển)
 * và lưu vào thư mục Pictures/Zenvid.
 */
@OptIn(UnstableApi::class)
object FrameCapture {

    suspend fun capture(ctx: Context, view: PlayerView, title: String): Result<Uri> = runCatching {
        val size = PlayerManager.player.videoSize
        require(size.width > 0 && size.height > 0) { "Chưa có hình để chụp" }
        // Giới hạn 4K để không tốn bộ nhớ
        val k = minOf(1f, 3840f / maxOf(size.width, size.height))
        val w = (size.width * size.pixelWidthHeightRatio * k).toInt().coerceAtLeast(1)
        val h = (size.height * k).toInt().coerceAtLeast(1)

        val bitmap = when (val surface = view.videoSurfaceView) {
            is SurfaceView -> {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val code = suspendCancellableCoroutine { cont ->
                    PixelCopy.request(surface, bmp, { cont.resume(it) }, Handler(Looper.getMainLooper()))
                }
                check(code == PixelCopy.SUCCESS) { "Không chụp được khung hình (mã $code)" }
                bmp
            }
            is TextureView -> surface.getBitmap(w, h) ?: error("Không chụp được khung hình")
            else -> error("Không chụp được khung hình")
        }
        withContext(Dispatchers.IO) { save(ctx, bitmap, title) }
    }

    private fun save(ctx: Context, bmp: Bitmap, title: String): Uri {
        val safe = title.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").trim().take(60).ifBlank { "video" }
        val pos = PlayerGestures.fmt(PlayerManager.player.currentPosition).replace(':', '.')
        val name = "$safe ($pos) " + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Zenvid")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error("Không tạo được file ảnh")
            ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            return uri
        }
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Zenvid").apply { mkdirs() }
        val f = File(dir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return Uri.fromFile(f)
    }
}
