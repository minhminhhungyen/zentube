package vn.zenvid.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.TimeUnit

/**
 * Cầu nối mạng cho NewPipeExtractor.
 * Chỉ gửi những gì YouTube cần để trả về nội dung: không có ID quảng cáo, không có cookie đăng nhập Google,
 * không có thư viện thống kê nào.
 */
class HttpDownloader(val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        // Thư viện chỉ cho YouTube dùng en-GB, khiến YouTube trả bản DỊCH tiếng Anh của tiêu đề/mô tả video Việt.
        // Đổi sang ngôn ngữ người dùng chọn để nhận tiêu đề gốc (nếu đọc lỗi, YouTube.kt sẽ thử lại bằng tiếng Anh).
        // Hiện chỉ hỗ trợ tiếng Việt (cần chuẩn hoá số người đăng ký / ngày đăng bên dưới cho thư viện đọc đúng)
        val lang = contentLanguage.takeIf { it == "vi" && !forceEnglish.get() && isYouTube(request.url()) }
        var data = request.dataToSend()
        var url = request.url()
        if (lang != null) {
            if (data != null) data = String(data, Charsets.UTF_8).replace("\"hl\":\"en-GB\"", "\"hl\":\"$lang\"").toByteArray(Charsets.UTF_8)
            url = url.replace(Regex("([?&])hl=en-GB"), "$1hl=$lang")
        }
        val builder = okhttp3.Request.Builder()
            .url(url)
            .method(request.httpMethod(), data?.toRequestBody())
            .header("User-Agent", USER_AGENT)

        for ((name, values) in request.headers()) {
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        if (lang != null) builder.header("Accept-Language", "$lang,$lang-${contentCountry};q=0.9")

        client.newCall(builder.build()).execute().use { res ->
            if (res.code == 429) {
                throw ReCaptchaException("YouTube tạm thời giới hạn yêu cầu từ mạng của bạn", request.url())
            }
            val text = res.body.string().let { if (lang != null) VietnameseText.normalize(it) else it }
            return Response(res.code, res.message, res.headers.toMultimap(), text, res.request.url.toString())
        }
    }

    companion object {
        /** Ngôn ngữ nội dung (vd. "vi"); "en" = để nguyên tiếng Anh như thư viện mặc định. */
        @Volatile var contentLanguage: String = "vi"
        @Volatile var contentCountry: String = "VN"

        /** Bật trên một luồng để buộc dùng tiếng Anh (khi bản tiếng Việt đọc bị lỗi). */
        val forceEnglish: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

        private fun isYouTube(url: String) = url.contains("youtube.com/") || url.contains("youtubei.googleapis.com/")

        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        fun newClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /** User-Agent cho luồng video – giống cách NewPipe làm (visionOS cần UA riêng, còn lại dùng UA máy tính). */
        fun userAgentForStream(url: String): String =
            if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) YoutubeParsingHelper.getVisionOsUserAgent(null)
            else USER_AGENT
    }
}

/**
 * Thư viện chỉ đọc được số và thời gian viết bằng tiếng Anh. Khi nhận nội dung tiếng Việt, đổi riêng các chuỗi
 * "4,2 Tr người đăng ký" -> "4.2M subscribers" và "3 ngày trước" -> "3 days ago" để thư viện đọc đúng
 * (app tự hiển thị lại bằng tiếng Việt). Tiêu đề, mô tả video giữ nguyên.
 */
object VietnameseText {
    // YouTube dùng cả dấu cách thường lẫn dấu cách không ngắt (U+00A0), và cả dạng viết đủ "triệu"
    private val SUBS = Regex("""(\d+(?:[.,]\d+)?)[\s\u00A0\u202F]?(N|Tr|T|nghìn|ngàn|triệu|tỷ)?[\s\u00A0\u202F](người đăng ký)""")
    // Chỉ đổi khi cả chuỗi JSON là mốc thời gian (tránh đổi nhầm tiêu đề video có chữ "… năm trước")
    private val AGO = Regex("""("(?:simpleText|text|content|publishedTimeText)":\s*")(Phát trực tiếp[\s\u00A0]|Đã phát trực tiếp[\s\u00A0]|Đã công chiếu[\s\u00A0])?(\d+)[\s\u00A0](giây|phút|giờ|ngày|tuần|tháng|năm)[\s\u00A0]trước(")""")
    private val UNITS = mapOf("giây" to "second", "phút" to "minute", "giờ" to "hour", "ngày" to "day", "tuần" to "week", "tháng" to "month", "năm" to "year")

    fun normalize(json: String): String {
        if (!json.contains("người đăng ký") && !json.contains(" trước\"")) return json
        var s = SUBS.replace(json) { m ->
            val num = m.groupValues[1].replace(',', '.')
            val unit = when (m.groupValues[2]) { "N", "nghìn", "ngàn" -> "K"; "Tr", "triệu" -> "M"; "T", "tỷ" -> "B"; else -> "" }
            // Số nguyên có dấu chấm ngăn cách hàng nghìn (vd. "1.234 người đăng ký") -> bỏ dấu chấm
            val clean = if (unit.isEmpty()) num.replace(".", "") else num
            "$clean$unit subscribers"
        }
        s = AGO.replace(s) { m ->
            val n = m.groupValues[3].toInt()
            val unit = UNITS.getValue(m.groupValues[4]) + if (n == 1) "" else "s"
            val prefix = if (m.groupValues[2].isNotEmpty()) "Streamed " else ""
            m.groupValues[1] + prefix + "$n $unit ago" + m.groupValues[5]
        }
        return s
    }
}
