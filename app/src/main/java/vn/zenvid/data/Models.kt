package vn.zenvid.data

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale

/** Một mục trong danh sách: video, kênh hoặc danh sách phát. */
sealed interface Entry {
    val url: String
}

data class VideoItem(
    override val url: String,
    val title: String,
    val uploader: String = "",
    val uploaderUrl: String? = null,
    val thumbnail: String? = null,
    val uploaderAvatar: String? = null,
    val durationSec: Long = -1,
    val views: Long = -1,
    val uploadedText: String? = null,
    val uploadedAt: Long = 0,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
) : Entry

data class ChannelItem(
    override val url: String,
    val name: String,
    val avatar: String?,
    val subscribers: Long = -1,
    val videoCount: Long = -1,
    val description: String? = null,
    val verified: Boolean = false,
) : Entry

data class PlaylistEntry(
    override val url: String,
    val name: String,
    val thumbnail: String?,
    val uploader: String?,
    val count: Long = -1,
) : Entry

// ---------------------------------------------------------------- chuyển đổi từ NewPipeExtractor

/** Chọn ảnh có kích thước gần với chiều cao mong muốn nhất (không nhỏ hơn nếu có thể). */
fun List<Image>?.bestUrl(targetHeight: Int = 360): String? {
    if (this.isNullOrEmpty()) return null
    val known = filter { it.height > 0 }
    if (known.isEmpty()) return last().url
    return (known.filter { it.height >= targetHeight }.minByOrNull { it.height }
        ?: known.maxBy { it.height }).url
}

private fun isLiveType(t: StreamType?) = t == StreamType.LIVE_STREAM || t == StreamType.AUDIO_LIVE_STREAM

fun StreamInfoItem.toVideo() = VideoItem(
    url = url,
    title = name ?: "",
    uploader = uploaderName ?: "",
    uploaderUrl = uploaderUrl,
    thumbnail = thumbnails.bestUrl(),
    uploaderAvatar = uploaderAvatars.bestUrl(88),
    durationSec = duration,
    views = viewCount,
    uploadedText = textualUploadDate,
    uploadedAt = uploadDate?.instant?.toEpochMilli() ?: 0,
    isLive = isLiveType(streamType),
    isShort = isShortFormContent,
)

fun StreamInfo.toVideo() = VideoItem(
    url = url,
    title = name ?: "",
    uploader = uploaderName ?: "",
    uploaderUrl = uploaderUrl,
    thumbnail = thumbnails.bestUrl(),
    uploaderAvatar = uploaderAvatars.bestUrl(88),
    durationSec = duration,
    views = viewCount,
    uploadedText = textualUploadDate,
    uploadedAt = uploadDate?.instant?.toEpochMilli() ?: 0,
    isLive = isLiveType(streamType),
    isShort = isShortFormContent,
)

fun InfoItem.toEntry(): Entry? = when (this) {
    is StreamInfoItem -> toVideo()
    is ChannelInfoItem -> ChannelItem(
        url = url, name = name ?: "", avatar = thumbnails.bestUrl(88),
        subscribers = subscriberCount, videoCount = streamCount,
        description = description, verified = isVerified,
    )
    is PlaylistInfoItem -> PlaylistEntry(
        url = url, name = name ?: "", thumbnail = thumbnails.bestUrl(),
        uploader = uploaderName, count = streamCount,
    )
    else -> null
}

// ---------------------------------------------------------------- định dạng hiển thị (tiếng Việt)

private val VI = Locale.forLanguageTag("vi-VN")

fun compactNumber(n: Long): String {
    fun f(d: Double): String =
        if (d >= 10) d.toLong().toString()
        else String.format(VI, "%.1f", Math.floor(d * 10) / 10).removeSuffix(",0")
    return when {
        n < 1_000 -> n.toString()
        n < 1_000_000 -> f(n / 1_000.0) + " N"
        n < 1_000_000_000 -> f(n / 1_000_000.0) + " Tr"
        else -> f(n / 1_000_000_000.0) + " T"
    }
}

fun viewsText(n: Long, live: Boolean = false): String? =
    if (n < 0) null else compactNumber(n) + if (live) " người đang xem" else " lượt xem"

fun subscribersText(n: Long): String? = if (n < 0) null else compactNumber(n) + " người đăng ký"

fun durationText(sec: Long): String? {
    if (sec <= 0) return null
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(VI, "%d:%02d:%02d", h, m, s) else String.format(VI, "%d:%02d", m, s)
}

fun relativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val diff = ((now - epochMs) / 1000).coerceAtLeast(0)
    return when {
        diff < 60 -> "Vừa xong"
        diff < 3600 -> "${diff / 60} phút trước"
        diff < 86400 -> "${diff / 3600} giờ trước"
        diff < 7 * 86400 -> "${diff / 86400} ngày trước"
        diff < 30 * 86400 -> "${diff / (7 * 86400)} tuần trước"
        diff < 365 * 86400 -> "${diff / (30 * 86400)} tháng trước"
        else -> "${diff / (365 * 86400)} năm trước"
    }
}

fun VideoItem.whenText(): String? = when {
    isLive -> "Trực tiếp"
    uploadedAt > 0 -> relativeTime(uploadedAt)
    else -> uploadedText?.takeIf { it.isNotBlank() }
}

fun VideoItem.metaLine(includeUploader: Boolean = true): String =
    listOfNotNull(
        uploader.takeIf { includeUploader && it.isNotBlank() },
        viewsText(views, isLive),
        whenText(),
    ).joinToString(" · ")

/** Lấy ID video từ URL YouTube (watch?v=, youtu.be/, shorts/). */
fun videoIdOf(url: String): String? {
    Regex("""(?:v=|youtu\.be/|shorts/|live/|embed/)([A-Za-z0-9_-]{11})""").find(url)?.let { return it.groupValues[1] }
    return null
}
