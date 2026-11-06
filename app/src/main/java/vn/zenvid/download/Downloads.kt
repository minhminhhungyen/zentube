package vn.zenvid.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.zenvid.Services
import vn.zenvid.data.DownloadEntry
import vn.zenvid.data.VideoItem
import vn.zenvid.data.YouTube
import java.io.File
import java.io.IOException

object Status {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val DONE = "done"
    const val FAILED = "failed"
}

/**
 * Quản lý tải xuống: hàng đợi lưu trong cơ sở dữ liệu, tải lần lượt từng video trong dịch vụ nền,
 * lưu file vào thư mục Movies/Zenvid (video) hoặc Music/Zenvid (âm thanh) của máy.
 */
object Downloads {
    private lateinit var app: Context
    private val dao get() = Services.db.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentId = -1L
    private var currentJob: Job? = null

    fun init(ctx: Context) { app = ctx.applicationContext }

    val all: Flow<List<DownloadEntry>> get() = dao.downloads()
    fun forUrl(url: String): Flow<List<DownloadEntry>> = dao.downloadsFor(url)
    suspend fun doneFor(url: String): DownloadEntry? = dao.doneDownload(url)?.takeIf { exists(it) }

    private fun tmpDir() = File(app.filesDir, "dl_tmp").apply { mkdirs() }
    private fun tmpVideo(id: Long) = File(tmpDir(), "$id.v")
    private fun tmpAudio(id: Long) = File(tmpDir(), "$id.a")
    private fun tmpOut(id: Long) = File(tmpDir(), "$id.out")

    fun startService() {
        runCatching { ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java)) }
    }

    /** Mở app: tiếp tục các lượt tải còn dở. */
    fun resumePending() = scope.launch {
        dao.requeueRunning()
        if (dao.queuedCount() > 0) withContext(Dispatchers.Main) { startService() }
    }

    suspend fun enqueue(v: VideoItem, kind: String, height: Int) {
        val url = YouTube.normalizeVideoUrl(v.url)
        dao.insertDownload(
            DownloadEntry(
                url = url, title = v.title, uploader = v.uploader, uploaderUrl = v.uploaderUrl, thumbnail = v.thumbnail,
                durationSec = v.durationSec, kind = kind, height = height, status = Status.QUEUED,
            )
        )
        withContext(Dispatchers.Main) { startService() }
    }

    suspend fun retry(d: DownloadEntry) {
        dao.updateDownload(d.copy(status = Status.QUEUED, error = null))
        withContext(Dispatchers.Main) { startService() }
    }

    /** Huỷ (nếu đang tải) và xoá cả file đã tải. */
    suspend fun remove(d: DownloadEntry) {
        if (d.id == currentId) currentJob?.cancel()
        withContext(Dispatchers.IO) {
            listOf(tmpVideo(d.id), tmpAudio(d.id), tmpOut(d.id)).forEach { it.delete() }
            d.fileUri?.let { deleteUri(it) }
        }
        dao.deleteDownload(d.id)
    }

    fun exists(d: DownloadEntry): Boolean {
        val uri = d.fileUri?.let(Uri::parse) ?: return false
        return when (uri.scheme) {
            "file" -> File(uri.path ?: return false).exists()
            else -> runCatching { app.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
        }
    }

    private fun deleteUri(s: String) {
        val uri = Uri.parse(s)
        runCatching { if (uri.scheme == "file") File(uri.path!!).delete() else app.contentResolver.delete(uri, null, null) }
    }

    // ================================================================== tải trong dịch vụ nền
    /** Chạy hết hàng đợi. [onProgress] để cập nhật thông báo. */
    suspend fun runQueue(onProgress: (title: String, done: Long, total: Long) -> Unit, onFinished: (DownloadEntry, Boolean) -> Unit) {
        while (true) {
            val d = dao.nextQueued() ?: break
            currentId = d.id
            coroutineScope {
                val job = launch { process(d, onProgress) }
                currentJob = job
                job.join()
            }
            currentJob = null
            currentId = -1
            dao.download(d.id)?.let { onFinished(it, it.status == Status.DONE) }
        }
    }

    private suspend fun process(start: DownloadEntry, onProgress: (String, Long, Long) -> Unit) {
        var d = start.copy(status = Status.RUNNING, error = null)
        dao.updateDownload(d)
        try {
            val info = YouTube.stream(d.url)
            val fetcher = ChunkFetcher(Services.http)
            val blocked = mutableSetOf<String>()
            repeat(4) {
                val plan = DownloadPicker.plan(info, d.kind, if (d.kind == Kind.AUDIO) 0 else d.height, blocked)
                    ?: throw IOException(if (blocked.isEmpty()) "Không có định dạng phù hợp để tải video này." else "YouTube đang chặn tải video này với mạng hiện tại.")
                // Đổi sang luồng khác thì phần đã tải dở không dùng lại được
                val vItag = plan.video?.itag ?: -1
                val aItag = plan.audio?.itag ?: -1
                if (vItag != d.videoItag || aItag != d.audioItag) {
                    tmpVideo(d.id).delete(); tmpAudio(d.id).delete()
                    d = d.copy(videoItag = vItag, audioItag = aItag, actualHeight = plan.height, downloadedBytes = 0)
                    dao.updateDownload(d)
                }
                try {
                    download(d, plan, fetcher, onProgress)
                    finish(d, plan)
                    return
                } catch (e: StreamBlockedException) {
                    blocked += plan.clients
                }
            }
            throw IOException("YouTube đang chặn tải video này với mạng hiện tại.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            dao.downloadStatus(d.id, Status.FAILED, if (e is IOException) e.message ?: "Lỗi mạng" else YouTube.friendlyError(e))
        }
    }

    private suspend fun download(d: DownloadEntry, plan: DownloadPlan, fetcher: ChunkFetcher, onProgress: (String, Long, Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val vUrl = plan.video?.content
            val aUrl = plan.audio?.content
            val vSize = vUrl?.let(fetcher::sizeOf) ?: 0
            val aSize = aUrl?.let(fetcher::sizeOf) ?: 0
            val total = vSize + aSize
            var done = tmpVideo(d.id).length() + tmpAudio(d.id).length()
            var lastUi = 0L
            val tick: suspend (Long) -> Unit = { n ->
                done += n
                val now = System.currentTimeMillis()
                if (now - lastUi > 700) {
                    lastUi = now
                    dao.downloadProgress(d.id, done, total)
                    onProgress(d.title, done, total)
                }
            }
            // Luồng gộp sẵn hoặc chỉ âm thanh: tải thẳng vào file video/audio tạm
            if (vUrl != null) fetcher.fetch(vUrl, tmpVideo(d.id), vSize, tick)
            if (aUrl != null) fetcher.fetch(aUrl, tmpAudio(d.id), aSize, tick)
            dao.downloadProgress(d.id, total, total)
        }

    private suspend fun finish(d: DownloadEntry, plan: DownloadPlan) = withContext(Dispatchers.IO) {
        val ready: File = when {
            plan.needsMux -> tmpOut(d.id).also { out ->
                out.delete()
                Muxer.mux(tmpVideo(d.id), tmpAudio(d.id), out, webm = plan.container == "webm")
            }
            plan.video != null -> tmpVideo(d.id)
            else -> tmpAudio(d.id)
        }
        val quality = if (d.kind == Kind.AUDIO) "audio" else "${plan.height}p"
        val name = safeName(d.title) + " [$quality]." + plan.extension
        val uri = save(ready, name, plan.mime, audio = plan.video == null)
        listOf(tmpVideo(d.id), tmpAudio(d.id), tmpOut(d.id)).forEach { it.delete() }
        val size = dao.download(d.id)?.totalBytes ?: 0
        dao.updateDownload(
            d.copy(
                status = Status.DONE, fileUri = uri, mime = plan.mime, error = null, actualHeight = plan.height,
                totalBytes = size, downloadedBytes = size,
            )
        )
    }

    private fun safeName(s: String) = s.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").trim().take(120).ifBlank { "video" }

    /** Lưu vào thư viện chung của máy (Android 10+), hoặc thư mục riêng của app trên máy cũ hơn. */
    private fun save(file: File, name: String, mime: String, audio: Boolean): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = app.contentResolver
            val collection = if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, (if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/Zenvid")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("Không tạo được file trong bộ nhớ máy")
            try {
                resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out, 256 * 1024) } }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw IOException("Không lưu được file: ${e.message}")
            }
            return uri.toString()
        }
        val dir = File(app.getExternalFilesDir(if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES), "Zenvid").apply { mkdirs() }
        val dest = File(dir, name)
        if (!file.renameTo(dest)) file.copyTo(dest, overwrite = true)
        return Uri.fromFile(dest).toString()
    }
}
