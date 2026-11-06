package vn.zenvid

import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import vn.zenvid.data.HttpDownloader
import vn.zenvid.data.VideoItem
import vn.zenvid.data.YouTube
import vn.zenvid.data.compactNumber
import vn.zenvid.data.durationText
import vn.zenvid.data.videoIdOf
import vn.zenvid.player.StreamPicker

/**
 * Kiểm thử thật với YouTube (cần mạng): tìm kiếm, lấy luồng video, chọn chất lượng,
 * tải thử vài KB từ luồng đã chọn, kênh, bảng tin, bình luận, đề xuất.
 */
class YouTubeTest {
    companion object {
        private val client = HttpDownloader.newClient()

        @BeforeClass @JvmStatic
        fun setup() = YouTube.init(HttpDownloader(client), "vi", "VN")
    }

    private fun status(url: String): Int {
        val req = Request.Builder().url(url).header("Range", "bytes=0-65535")
            .header("User-Agent", HttpDownloader.userAgentForStream(url)).build()
        client.newCall(req).execute().use { r -> r.body.bytes(); return r.code }
    }

    /** Giống trình phát: thử luồng, bị 403 thì loại máy khách đó và chọn lại. */
    private fun playable(info: org.schabi.newpipe.extractor.stream.StreamInfo, h: Int): Pair<Boolean, String> {
        val blocked = mutableSetOf<String>()
        val log = StringBuilder()
        repeat(6) {
            val p = runCatching { StreamPicker.pick(info, h, true, blocked) }.getOrElse { return false to "$log hết lựa chọn" }
            val urls = listOfNotNull(p.video?.content, p.audio?.content, p.muxed?.content)
            if (urls.isEmpty()) return true to "$log HLS/DASH"
            val codes = urls.map(::status)
            log.append(StreamPicker.clientsOf(p)).append("=").append(codes).append(" ")
            if (codes.all { it == 200 || it == 206 }) return true to log.toString()
            blocked += StreamPicker.clientsOf(p)
        }
        return false to log.toString()
    }

    @Test fun formatting() {
        assertEquals("1,2 Tr", compactNumber(1_234_567))
        assertEquals("12 N", compactNumber(12_400))
        assertEquals("999", compactNumber(999))
        assertEquals("1:02:03", durationText(3723))
        assertEquals("3:05", durationText(185))
        assertEquals("dQw4w9WgXcQ", videoIdOf("https://youtu.be/dQw4w9WgXcQ?t=3"))
        assertEquals("abcdefghijk", videoIdOf("https://www.youtube.com/shorts/abcdefghijk"))
    }

    @Test fun searchAndPlay() = runBlocking {
        val r = YouTube.search("Sơn Tùng M-TP", YouTube.Filter.VIDEOS)
        val videos = r.page.items.filterIsInstance<VideoItem>()
        assertTrue("không có kết quả tìm kiếm", videos.size >= 5)
        assertNotNull(r.page.next)
        val more = r.more.load(r.page.next!!)
        assertTrue("trang 2 trống", more.items.isNotEmpty())

        val info = YouTube.stream(videos.first().url)
        val q = StreamPicker.qualities(info)
        println("qualities=$q")
        assertTrue(q.isNotEmpty())

        for (h in listOf(1080, 720, 360)) {
            val p = StreamPicker.pick(info, h, preferAvc = true)
            println("max $h -> ${p.height}p video=${p.video?.codec} audio=${p.audio?.codec} muxed=${p.muxed != null} clients=${StreamPicker.clientsOf(p)}")
            assertTrue(p.height <= h || p.height == q.min())
            assertEquals("video và âm thanh phải cùng máy khách", 1, StreamPicker.clientsOf(p).size)
        }
        // Mạng máy chủ (datacenter) thường bị YouTube chặn luồng; chỉ in ra để tham khảo
        println("phát thử '${info.name}': ${playable(info, 720)}")
    }

    @Test fun playbackKnownVideo() = runBlocking {
        val info = YouTube.stream("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val (ok, log) = playable(info, 1080)
        println("Rick: $ok $log")
        assertTrue("không tải được luồng: $log", ok)
    }

    @Test fun suggestions() = runBlocking {
        assertTrue(YouTube.suggestions("nhạc").isNotEmpty())
    }

    @Test fun channelAndFeed() = runBlocking {
        val ch = YouTube.channel("https://www.youtube.com/@MrBeast")
        val tabs = YouTube.channelTabs(ch)
        println("tabs=" + tabs.map { it.label })
        assertTrue(tabs.isNotEmpty())
        val (page, _) = YouTube.channelTab(tabs.first().handler)
        assertTrue(page.items.isNotEmpty())

        val feed = YouTube.subscriptionFeed(listOf(ch.url, "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"))
        println("feed=${feed.items.size} failed=${feed.failed}")
        assertTrue(feed.items.size >= 10)
        assertTrue(feed.items.first().uploadedAt >= feed.items.last().uploadedAt)
    }

    @Test fun kiosks() = runBlocking {
        for ((id, label) in YouTube.kiosks) {
            val r = runCatching { YouTube.kiosk(id).first.items.size }
            println("kiosk $label: ${r.getOrElse { YouTube.friendlyError(it) }}")
        }
        assertTrue(YouTube.kiosk("live").first.items.isNotEmpty())
    }

    @Test fun commentsAndRecommendations() = runBlocking {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val c = YouTube.comments(url)
        assertTrue(c.page.items.isNotEmpty())
        val rec = YouTube.recommendations(listOf(url), emptySet())
        println("recommendations=${rec.size}")
        assertTrue(rec.size >= 5)
        assertEquals(YouTube.LinkKind.VIDEO, YouTube.linkKind("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(YouTube.LinkKind.CHANNEL, YouTube.linkKind("https://www.youtube.com/@MrBeast"))
    }

    @Test fun subtitlesReachable() = runBlocking {
        val info = YouTube.stream("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        val s = info.subtitles.firstOrNull() ?: return@runBlocking
        val req = Request.Builder().url(s.content).build()
        client.newCall(req).execute().use { r -> println("subtitle ${s.languageTag}/${s.format} -> HTTP ${r.code}, ${r.body.string().length} ký tự") }
    }
}
