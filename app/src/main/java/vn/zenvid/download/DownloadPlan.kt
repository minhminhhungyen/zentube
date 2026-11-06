package vn.zenvid.download

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import vn.zenvid.data.HttpDownloader
import vn.zenvid.player.StreamPicker
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object Kind {
    const val VIDEO = "video"
    const val AUDIO = "audio"
}

/**
 * Những gì cần tải cho một lượt tải:
 *  - video + âm thanh tách rời -> tải cả hai rồi ghép (MP4: H.264+AAC, hoặc WebM: VP9+Opus cho 1440p/4K)
 *  - một luồng gộp sẵn (360p) hoặc chỉ âm thanh -> tải thẳng, không cần ghép
 */
data class DownloadPlan(
    val video: VideoStream?,
    val audio: AudioStream?,
    val container: String, // "mp4" | "webm" | "m4a"
    val height: Int,
) {
    val mime get() = when (container) {
        "mp4" -> "video/mp4"
        "webm" -> if (video != null) "video/webm" else "audio/webm"
        else -> "audio/mp4"
    }
    val extension get() = if (container == "webm" && video == null) "weba" else container
    val clients: Set<String> get() = setOfNotNull(video?.content?.let(StreamPicker::clientOf), audio?.content?.let(StreamPicker::clientOf))
    val needsMux get() = video != null && audio != null
}

/** Luồng bị YouTube từ chối (403) – thử luồng của "máy khách" khác. */
class StreamBlockedException : IOException("YouTube từ chối luồng tải")

object DownloadPicker {
    private fun ok(s: org.schabi.newpipe.extractor.stream.Stream, blocked: Set<String>) =
        s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && s.isUrl && StreamPicker.clientOf(s.content) !in blocked

    private fun isAvc(v: VideoStream) = v.codec?.lowercase()?.startsWith("avc") == true || (v.codec.isNullOrBlank() && v.format == MediaFormat.MPEG_4)
    private fun isVp9(v: VideoStream) = v.codec?.lowercase()?.let { it.startsWith("vp9") || it.startsWith("vp09") } == true

    fun plan(info: StreamInfo, kind: String, maxHeight: Int, blocked: Set<String> = emptySet()): DownloadPlan? {
        val audios = info.audioStreams.filter { ok(it, blocked) }
            .filter { it.audioTrackType == null || it.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL }
            .ifEmpty { info.audioStreams.filter { ok(it, blocked) } }
        val m4a = audios.filter { it.format == MediaFormat.M4A }.maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }
        val opus = audios.filter { it.format == MediaFormat.WEBMA_OPUS || it.format == MediaFormat.WEBMA }.maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }

        if (kind == Kind.AUDIO) {
            return when {
                m4a != null -> DownloadPlan(null, m4a, "m4a", 0)
                opus != null -> DownloadPlan(null, opus, "webm", 0)
                else -> null
            }
        }

        // Video: ưu tiên H.264 (MP4) – xem được ở mọi nơi; trên 1080p YouTube chỉ có VP9 -> WebM
        val videos = info.videoOnlyStreams.filter { ok(it, blocked) && StreamPicker.heightOf(it) in 1..maxHeight }
        val avc = videos.filter(::isAvc)
        val vp9 = videos.filter(::isVp9)
        val bestAvc = avc.maxWithOrNull(compareBy<VideoStream>({ StreamPicker.heightOf(it) }, { it.fps.coerceAtMost(60) }, { it.bitrate }))
        val bestVp9 = vp9.maxWithOrNull(compareBy<VideoStream>({ StreamPicker.heightOf(it) }, { it.fps.coerceAtMost(60) }, { it.bitrate }))

        // Âm thanh nên cùng "máy khách" với video để không bị chặn một nửa
        fun sameClient(v: VideoStream, list: List<AudioStream>, fallback: AudioStream?) =
            list.filter { StreamPicker.clientOf(it.content) == StreamPicker.clientOf(v.content) }
                .maxByOrNull { maxOf(it.averageBitrate, it.bitrate) } ?: fallback

        if (bestVp9 != null && opus != null && StreamPicker.heightOf(bestVp9) > (bestAvc?.let(StreamPicker::heightOf) ?: 0)) {
            val a = sameClient(bestVp9, audios.filter { it.format == MediaFormat.WEBMA_OPUS || it.format == MediaFormat.WEBMA }, opus)!!
            return DownloadPlan(bestVp9, a, "webm", StreamPicker.heightOf(bestVp9))
        }
        if (bestAvc != null && m4a != null) {
            val a = sameClient(bestAvc, audios.filter { it.format == MediaFormat.M4A }, m4a)!!
            return DownloadPlan(bestAvc, a, "mp4", StreamPicker.heightOf(bestAvc))
        }

        // Dự phòng: luồng gộp sẵn (thường 360p)
        val muxed = info.videoStreams.filter { ok(it, blocked) && it.format == MediaFormat.MPEG_4 }
            .filter { StreamPicker.heightOf(it) <= maxHeight }.maxByOrNull(StreamPicker::heightOf)
            ?: info.videoStreams.filter { ok(it, blocked) && it.format == MediaFormat.MPEG_4 }.minByOrNull(StreamPicker::heightOf)
        return muxed?.let { DownloadPlan(it, null, "mp4", StreamPicker.heightOf(it)) }
    }
}

/** Tải một luồng theo từng khúc 8 MB (YouTube giới hạn tốc độ với yêu cầu quá lớn), tiếp tục được nếu bị ngắt. */
class ChunkFetcher(private val http: OkHttpClient, private val chunk: Long = 8L * 1024 * 1024) {

    /** Kích thước luồng (byte), -1 nếu không rõ. */
    fun sizeOf(url: String): Long {
        val req = Request.Builder().url(url).header("Range", "bytes=0-0").header("User-Agent", HttpDownloader.userAgentForStream(url)).build()
        http.newCall(req).execute().use { res ->
            if (res.code == 403) throw StreamBlockedException()
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            return res.header("Content-Range")?.substringAfter("/")?.toLongOrNull() ?: res.body.contentLength()
        }
    }

    /** Tải [url] vào [file] (nối tiếp phần đã có). [onBytes] nhận số byte vừa tải thêm. */
    suspend fun fetch(url: String, file: File, total: Long, onBytes: suspend (Long) -> Unit) {
        var pos = file.length()
        if (total in 1..pos) return // đã đủ
        FileOutputStream(file, true).use { out ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val end = if (total > 0) minOf(pos + chunk - 1, total - 1) else pos + chunk - 1
                val req = Request.Builder().url(url).header("Range", "bytes=$pos-$end")
                    .header("User-Agent", HttpDownloader.userAgentForStream(url)).build()
                var got = 0L
                http.newCall(req).execute().use { res ->
                    when {
                        res.code == 403 -> throw StreamBlockedException()
                        res.code == 416 -> return
                        !res.isSuccessful -> throw IOException("Lỗi máy chủ khi tải (HTTP ${res.code})")
                    }
                    res.body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                            pos += n
                            onBytes(n.toLong())
                            if (got % (1024 * 1024) < 65536) currentCoroutineContext().ensureActive()
                        }
                    }
                }
                if (got == 0L) break
                if (total > 0 && pos >= total) break
                if (total <= 0 && got < chunk) break
            }
            out.flush()
        }
        if (total > 0 && file.length() < total) throw IOException("Tải chưa đủ dữ liệu, thử lại sau")
    }
}
