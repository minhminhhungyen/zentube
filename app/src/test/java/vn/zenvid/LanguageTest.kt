package vn.zenvid

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import vn.zenvid.data.HttpDownloader
import vn.zenvid.data.VideoItem
import vn.zenvid.data.YouTube
import vn.zenvid.data.metaLine

/** Nội dung phải về bằng tiếng Việt (tiêu đề gốc, ngày đăng tiếng Việt) và mọi mục đều tải được. */
class LanguageTest {
    companion object {
        @BeforeClass @JvmStatic fun setup() = YouTube.init(HttpDownloader(HttpDownloader.newClient()), "vi", "VN")
        private val VI = Regex("[ăâđêôơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ]", RegexOption.IGNORE_CASE)
        private fun viShare(titles: List<String>) = titles.count { VI.containsMatchIn(it) }.toFloat() / titles.size.coerceAtLeast(1)
    }

    @Test fun searchReturnsOriginalVietnameseTitles() = runBlocking {
        val items = YouTube.search("vtv24", YouTube.Filter.VIDEOS).page.items.filterIsInstance<VideoItem>().take(10)
        items.take(5).forEach { println("search: ${it.title.take(60)}  ‹${it.metaLine(false)}›") }
        assertTrue("tiêu đề VTV24 phải là tiếng Việt, không phải bản dịch tiếng Anh", viShare(items.map { it.title }) >= 0.7f)
        val info = YouTube.stream(items.first().url)
        println("stream: ${info.name.take(60)} | views=${info.viewCount} | errors=${info.errors.size} | desc=${info.description.content().take(60).replace("\n", " ")}")
        assertTrue(VI.containsMatchIn(info.name))
        assertTrue(info.viewCount > 0)
        assertTrue(info.audioStreams.isNotEmpty())
    }

    @Test fun everyHomeTabLoads() = runBlocking {
        for ((id, label) in YouTube.kiosks) {
            val items = YouTube.kiosk(id).first.items.filterIsInstance<VideoItem>()
            println("$label: ${items.size} video, ${(viShare(items.map { it.title }) * 100).toInt()}% tiêu đề có dấu tiếng Việt – ${items.firstOrNull()?.let { it.title.take(40) + " ‹" + it.metaLine(false) + "›" }}")
            assertTrue("$label trống", items.isNotEmpty())
        }
        val starter = YouTube.starter()
        println("Đề xuất lần đầu: ${starter.size} video, ${(viShare(starter.map { it.title }) * 100).toInt()}% tiếng Việt")
        assertTrue(starter.size >= 20)
        assertTrue(viShare(starter.map { it.title }) >= 0.5f)
    }

    @Test fun commentsAndChannel() = runBlocking {
        val url = YouTube.search("vtv24", YouTube.Filter.VIDEOS).page.items.filterIsInstance<VideoItem>().first().url
        val c = YouTube.comments(url)
        println("comments: " + c.page.items.take(2).joinToString(" | ") { it.commentText.content().take(30) + " (" + it.textualUploadDate + ")" })
        val ch = YouTube.channel("https://www.youtube.com/@VTV24")
        val tab = YouTube.channelTab(YouTube.channelTabs(ch).first().handler).first.items.filterIsInstance<VideoItem>()
        println("channel: ${ch.name} subs=${ch.subscriberCount} – ${tab.firstOrNull()?.title?.take(50)}")
        assertTrue(tab.isNotEmpty())
        assertTrue(viShare(tab.map { it.title }) >= 0.7f)
    }

    /** Phương án dự phòng khi mục Podcast / Trò chơi / Trailer bị YouTube đổi định dạng. */
    @Test fun kioskFallbackQueries() = runBlocking {
        for (q in listOf("podcast tiếng Việt", "game việt", "trailer phim mới")) {
            val items = YouTube.search(q, YouTube.Filter.VIDEOS).page.items.filterIsInstance<VideoItem>().filter { !it.isLive }
            println("dự phòng \"$q\": ${items.size} video – ${items.firstOrNull()?.title?.take(50)}")
            assertTrue("$q trống", items.size >= 5)
        }
    }
}
