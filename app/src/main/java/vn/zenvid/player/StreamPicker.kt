package vn.zenvid.player

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** Kết quả chọn luồng: hoặc video+âm thanh tách rời, hoặc 1 luồng gộp, hoặc HLS/DASH. */
data class Picked(
    val video: VideoStream? = null,
    val audio: AudioStream? = null,
    val muxed: VideoStream? = null,
    val hls: String? = null,
    val dash: String? = null,
    val height: Int = 0,
)

/** Logic chọn chất lượng – thuần Kotlin để chạy được trong kiểm thử JVM. */
object StreamPicker {

    /** "Máy khách" YouTube đã cấp đường dẫn luồng (ANDROID, IOS, VISIONOS…), lấy từ tham số c= */
    fun clientOf(url: String?): String = url?.let { Regex("[?&]c=([A-Z_0-9]+)").find(it)?.groupValues?.get(1) } ?: "OTHER"

    /** Mức chất lượng theo nhãn YouTube ("1080p60" -> 1080); video không phải 16:9 có chiều cao thật khác nhãn. */
    fun heightOf(v: VideoStream): Int =
        v.getResolution().takeWhile { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 } ?: v.height.coerceAtLeast(0)

    private fun isLive(info: StreamInfo) =
        info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM

    private fun progressive(v: List<VideoStream>, blocked: Set<String> = emptySet()) =
        v.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl && clientOf(it.content) !in blocked }

    /** Các mức chất lượng có thể chọn (giảm dần). */
    fun qualities(info: StreamInfo): List<Int> {
        if (isLive(info)) return emptyList()
        return (progressive(info.videoOnlyStreams) + progressive(info.videoStreams))
            .map(::heightOf).filter { it > 0 }.distinct().sortedDescending()
    }

    private fun codecScore(v: VideoStream, preferAvc: Boolean): Int {
        val c = v.codec?.lowercase() ?: ""
        return when {
            c.startsWith("avc") -> if (preferAvc) 3 else 2
            c.startsWith("vp9") || c.startsWith("vp09") -> if (preferAvc) 2 else 3
            c.startsWith("av01") -> 0 // AV1 tốn pin trên nhiều máy, chỉ dùng khi không còn lựa chọn khác
            v.format == MediaFormat.MPEG_4 -> if (preferAvc) 3 else 2
            else -> 1
        }
    }

    fun bestAudio(audio: List<AudioStream>, preferAac: Boolean, blocked: Set<String> = emptySet(), client: String? = null): AudioStream? {
        val usable = audio.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl && clientOf(it.content) !in blocked &&
                (client == null || clientOf(it.content) == client)
        }
        if (usable.isEmpty()) return null
        // Ưu tiên bản âm thanh gốc (không lồng tiếng, không mô tả)
        val original = usable.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
            .ifEmpty { usable }
        return original.sortedWith(
            compareByDescending<AudioStream> { if (preferAac) it.format == MediaFormat.M4A else true }
                .thenByDescending { maxOf(it.averageBitrate, it.bitrate) }
        ).first()
    }

    /**
     * Chọn luồng phù hợp nhất. [blocked] là các "máy khách" mà YouTube vừa từ chối (lỗi 403):
     * khi đó thử luồng của máy khách khác, cuối cùng mới dùng HLS.
     */
    fun pick(info: StreamInfo, maxHeight: Int, preferAvc: Boolean, blocked: Set<String> = emptySet()): Picked {
        if (isLive(info)) {
            info.hlsUrl?.takeIf { it.isNotBlank() }?.let { return Picked(hls = it) }
            info.dashMpdUrl?.takeIf { it.isNotBlank() }?.let { return Picked(dash = it) }
        }

        val allVideos = progressive(info.videoOnlyStreams, blocked).filter { heightOf(it) > 0 }
        // Video và âm thanh nên cùng một máy khách để tránh một nửa bị chặn
        val client = allVideos.map { clientOf(it.content) }.firstOrNull { c ->
            bestAudio(info.audioStreams, preferAvc, blocked, c) != null
        }
        val videos = if (client != null) allVideos.filter { clientOf(it.content) == client } else allVideos
        val audio = bestAudio(info.audioStreams, preferAvc, blocked, client)

        if (audio != null && videos.isNotEmpty()) {
            val under = videos.filter { heightOf(it) <= maxHeight }
            val pool = under.ifEmpty { videos.filter { heightOf(it) == videos.minOf(::heightOf) } }
            val top = pool.maxOf(::heightOf)
            val choice = pool.filter { heightOf(it) == top }.sortedWith(
                compareByDescending<VideoStream> { codecScore(it, preferAvc) }
                    .thenByDescending { it.fps.coerceAtMost(60) }
                    .thenByDescending { it.bitrate }
            ).first()
            return Picked(video = choice, audio = audio, height = heightOf(choice))
        }

        val muxed = progressive(info.videoStreams, blocked)
        if (muxed.isNotEmpty()) {
            val m = muxed.filter { heightOf(it) <= maxHeight }.maxByOrNull(::heightOf) ?: muxed.minBy(::heightOf)
            return Picked(muxed = m, height = heightOf(m))
        }

        // Chỉ có âm thanh (ví dụ bài nhạc) – phát âm thanh như một "video" không hình
        if (audio != null && info.videoStreams.isEmpty() && info.videoOnlyStreams.isEmpty()) {
            return Picked(audio = audio)
        }

        if (HLS !in blocked) info.hlsUrl?.takeIf { it.isNotBlank() }?.let { return Picked(hls = it) }
        if (DASH !in blocked) info.dashMpdUrl?.takeIf { it.isNotBlank() }?.let { return Picked(dash = it) }
        throw NoStreamException()
    }

    const val HLS = "#HLS"
    const val DASH = "#DASH"

    /** Các "máy khách" mà một lựa chọn đang dùng – để đánh dấu bị chặn nếu YouTube trả lỗi 403. */
    fun clientsOf(p: Picked): Set<String> = buildSet {
        p.video?.let { add(clientOf(it.content)) }
        p.audio?.let { add(clientOf(it.content)) }
        p.muxed?.let { add(clientOf(it.content)) }
        if (p.hls != null) add(HLS)
        if (p.dash != null) add(DASH)
    }
}

class NoStreamException : Exception("Không tìm thấy luồng video phát được")
