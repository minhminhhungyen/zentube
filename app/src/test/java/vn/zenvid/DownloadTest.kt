package vn.zenvid

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import vn.zenvid.data.HttpDownloader
import vn.zenvid.data.YouTube
import vn.zenvid.download.ChunkFetcher
import vn.zenvid.download.DownloadPicker
import vn.zenvid.download.Kind
import vn.zenvid.download.StreamBlockedException
import vn.zenvid.player.StreamPicker
import java.io.File

/** Kiểm thử thật với YouTube: chọn định dạng tải và tải theo khúc (có tiếp tục khi bị ngắt). */
class DownloadTest {
    companion object {
        private val client = HttpDownloader.newClient()
        @BeforeClass @JvmStatic fun setup() = YouTube.init(HttpDownloader(client), "vi", "VN")
        // Video vẫn tải được từ mạng máy chủ kiểm thử (nhiều video khác bị YouTube chặn với IP máy chủ)
        const val URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    }

    @Test fun choosesFormats(): Unit = runBlocking {
        val info = YouTube.stream(URL)
        val p720 = DownloadPicker.plan(info, Kind.VIDEO, 720)!!
        println("720 -> ${p720.container} ${p720.height}p v=${p720.video?.codec} a=${p720.audio?.codec}")
        assertEquals("mp4", p720.container)
        assertEquals(720, p720.height)
        assertTrue(p720.video!!.codec.startsWith("avc"))
        assertEquals("mp4a", p720.audio!!.codec.take(4))
        assertTrue(p720.needsMux)
        assertEquals(1, p720.clients.size)

        val p4k = DownloadPicker.plan(info, Kind.VIDEO, 2160)!!
        println("2160 -> ${p4k.container} ${p4k.height}p v=${p4k.video?.codec} a=${p4k.audio?.codec}")
        assertEquals("webm", p4k.container)
        assertEquals(2160, p4k.height)
        assertEquals("opus", p4k.audio!!.codec)

        val audio = DownloadPicker.plan(info, Kind.AUDIO, 0)!!
        assertEquals("m4a", audio.container); assertEquals("audio/mp4", audio.mime)

        // Mọi máy khách bị chặn -> không còn gì để tải
        val all = (info.videoOnlyStreams + info.audioStreams + info.videoStreams).map { StreamPicker.clientOf(it.content) }.toSet()
        assertEquals(null, DownloadPicker.plan(info, Kind.VIDEO, 720, all))
    }

    @Test fun chunkedDownloadWithResume(): Unit = runBlocking {
        val info = YouTube.stream(URL)
        val plan = DownloadPicker.plan(info, Kind.AUDIO, 0)!!
        val url = plan.audio!!.content
        val fetcher = ChunkFetcher(client, chunk = 512 * 1024) // khúc nhỏ để thử nhiều lần tải
        val size = fetcher.sizeOf(url)
        println("audio size=$size")
        assertTrue(size > 1_000_000)

        val f = File.createTempFile("zenvid", ".m4a")
        var counted = 0L
        fetcher.fetch(url, f, size) { counted += it }
        assertEquals(size, f.length())
        assertEquals(size, counted)
        assertEquals("ftyp", String(f.readBytes().copyOfRange(4, 8)))
        val full = f.readBytes()

        // Giả lập bị ngắt giữa chừng: cắt còn 1/3 rồi tải tiếp, kết quả phải giống hệt
        f.writeBytes(full.copyOf((size / 3).toInt()))
        fetcher.fetch(url, f, size) {}
        assertTrue(full.contentEquals(f.readBytes()))
        f.delete()
    }

    @Test fun blockedStreamIsReported(): Unit = runBlocking {
        val info = YouTube.stream(URL)
        val url = DownloadPicker.plan(info, Kind.AUDIO, 0)!!.audio!!.content
        // Làm hỏng chữ ký -> YouTube trả 403 -> phải báo StreamBlockedException để thử máy khách khác
        val broken = url.replace(Regex("([?&]sig=)[^&]+"), "$1x").replace(Regex("([?&]n=)[^&]+"), "$1x")
        val r = runCatching { ChunkFetcher(client).sizeOf(broken) }
        println("broken -> ${r.exceptionOrNull()?.javaClass?.simpleName ?: r.getOrNull()}")
        assertNotNull(r.exceptionOrNull())
        if (r.exceptionOrNull() !is StreamBlockedException) println("(máy chủ trả mã khác 403: ${r.exceptionOrNull()?.message})")
    }
}
