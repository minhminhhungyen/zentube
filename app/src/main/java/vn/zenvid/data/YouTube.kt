package vn.zenvid.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Một trang kết quả và "con trỏ" để tải trang tiếp theo. */
data class PageResult<T>(val items: List<T>, val next: Page?)

/** Kho chung để tải "trang tiếp theo" của bất kỳ danh sách nào. */
fun interface MoreLoader<T> {
    suspend fun load(page: Page): PageResult<T>
}

data class ChannelTab(val label: String, val handler: ListLinkHandler)

/**
 * Toàn bộ việc lấy dữ liệu từ YouTube. Không dùng API của Google, không cần khoá, không đăng nhập.
 * Mọi hàm đều chạy trên luồng IO.
 */
object YouTube {
    val service: StreamingService get() = ServiceList.YouTube

    fun init(downloader: HttpDownloader, language: String, country: String) {
        HttpDownloader.contentLanguage = language
        HttpDownloader.contentCountry = country
        NewPipe.init(downloader, Localization(language, country), ContentCountry(country))
    }

    fun setRegion(language: String, country: String) {
        HttpDownloader.contentLanguage = language
        HttpDownloader.contentCountry = country
        feedCache.clear()
        NewPipe.setupLocalization(Localization(language, country), ContentCountry(country))
    }

    /**
     * Chạy lời gọi thư viện trên luồng IO. Lần đầu xin nội dung bằng tiếng Việt (tiêu đề gốc);
     * nếu thư viện đọc trang tiếng Việt bị lỗi thì tự thử lại bằng tiếng Anh.
     */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            // YouTube trả trang chặn (HTML) khi bị giới hạn – thử lại ngay chỉ làm nặng thêm
            val blocked = e.message?.contains("Got HTML document") == true
            val retryable = !blocked && e !is ContentNotAvailableException && e !is ReCaptchaException && e !is IOException &&
                (e is org.schabi.newpipe.extractor.exceptions.ExtractionException || e is RuntimeException)
            if (!retryable || HttpDownloader.contentLanguage == "en") throw e
            HttpDownloader.forceEnglish.set(true)
            try { block() } finally { HttpDownloader.forceEnglish.set(false) }
        }
    }

    // ------------------------------------------------------------ tìm kiếm
    object Filter {
        const val ALL = "all"
        const val VIDEOS = "videos"
        const val CHANNELS = "channels"
        const val PLAYLISTS = "playlists"
    }

    data class SearchResult(val page: PageResult<Entry>, val more: MoreLoader<Entry>, val suggestion: String?)

    suspend fun search(query: String, filter: String = Filter.ALL): SearchResult = io {
        val handler: SearchQueryHandler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = org.schabi.newpipe.extractor.search.SearchInfo.getInfo(service, handler)
        val more = MoreLoader<Entry> { page ->
            io {
                val p = org.schabi.newpipe.extractor.search.SearchInfo.getMoreItems(service, handler, page)
                PageResult(p.items.mapNotNull { it.toEntry() }, p.nextPage.takeIf { p.hasNextPage() })
            }
        }
        SearchResult(
            PageResult(info.relatedItems.mapNotNull { it.toEntry() }, info.nextPage.takeIf { info.hasNextPage() }),
            more,
            info.searchSuggestion?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun suggestions(query: String): List<String> = io {
        runCatching { service.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------ video
    private val streamCache = ConcurrentHashMap<String, Pair<Long, StreamInfo>>()

    suspend fun stream(url: String, useCache: Boolean = false): StreamInfo = io {
        val now = System.currentTimeMillis()
        if (useCache) streamCache[url]?.let { (t, info) -> if (now - t < 30 * 60_000) return@io info }
        val info = StreamInfo.getInfo(service, url)
        streamCache[url] = now to info
        info
    }

    data class CommentsResult(val page: PageResult<CommentsInfoItem>, val more: MoreLoader<CommentsInfoItem>, val disabled: Boolean, val count: Int)

    suspend fun comments(url: String): CommentsResult = io {
        val info = CommentsInfo.getInfo(service, url)
        val more = MoreLoader<CommentsInfoItem> { page ->
            io {
                val p = CommentsInfo.getMoreItems(service, info, page)
                PageResult(p.items, p.nextPage.takeIf { p.hasNextPage() })
            }
        }
        CommentsResult(
            PageResult(info.relatedItems, info.nextPage.takeIf { info.hasNextPage() }),
            more, info.isCommentsDisabled, info.commentsCount,
        )
    }

    // ------------------------------------------------------------ kênh
    suspend fun channel(url: String): ChannelInfo = io { ChannelInfo.getInfo(service, url) }

    fun channelTabs(info: ChannelInfo): List<ChannelTab> {
        val labels = linkedMapOf(
            ChannelTabs.VIDEOS to "Video",
            ChannelTabs.SHORTS to "Shorts",
            ChannelTabs.LIVESTREAMS to "Trực tiếp",
            ChannelTabs.PLAYLISTS to "Danh sách phát",
        )
        return labels.mapNotNull { (key, label) ->
            info.tabs.firstOrNull { key in it.contentFilters }?.let { ChannelTab(label, it) }
        }
    }

    suspend fun channelTab(handler: ListLinkHandler): Pair<PageResult<Entry>, MoreLoader<Entry>> = io {
        val info = ChannelTabInfo.getInfo(service, handler)
        val more = MoreLoader<Entry> { page ->
            io {
                val p = ChannelTabInfo.getMoreItems(service, handler, page)
                PageResult(p.items.mapNotNull { it.toEntry() }, p.nextPage.takeIf { p.hasNextPage() })
            }
        }
        PageResult(info.relatedItems.mapNotNull { it.toEntry() }, info.nextPage.takeIf { info.hasNextPage() }) to more
    }

    fun normalizeChannelUrl(url: String): String =
        runCatching { service.channelLHFactory.fromUrl(url).url }.getOrDefault(url)

    // ------------------------------------------------------------ danh sách phát
    suspend fun playlist(url: String): Pair<PlaylistInfo, MoreLoader<Entry>> = io {
        val info = PlaylistInfo.getInfo(service, url)
        val more = MoreLoader<Entry> { page ->
            io {
                val p = PlaylistInfo.getMoreItems(service, url, page)
                PageResult<Entry>(p.items.map { it.toVideo() }, p.nextPage.takeIf { p.hasNextPage() })
            }
        }
        info to more
    }

    // ------------------------------------------------------------ mục khám phá (kiosk)
    /** Trang "Thịnh hành" chung đã bị YouTube gỡ bỏ, nên chỉ dùng các mục còn hoạt động. */
    val kiosks = listOf(
        "trending_music" to "Âm nhạc",
        "live" to "Trực tiếp",
        "trending_gaming" to "Trò chơi",
        "trending_podcasts_episodes" to "Podcast",
        "trending_movies_and_shows" to "Trailer phim",
    )

    /**
     * Từ khoá dự phòng cho từng mục: khi YouTube không có bảng xếp hạng cho quốc gia này
     * (vd. Âm nhạc ở Việt Nam) hoặc đổi định dạng trang khiến mục bị lỗi / trống (hay gặp ở Podcast),
     * app lấy video qua tìm kiếm thay vì báo lỗi.
     */
    private fun fallbackQuery(id: String): String? {
        val vn = HttpDownloader.contentCountry == "VN"
        return when (id) {
            "trending_music" -> if (vn) "nhạc Việt mới nhất" else "new music"
            "trending_podcasts_episodes" -> if (vn) "podcast tiếng Việt" else "podcast episode"
            "trending_gaming" -> if (vn) "game việt" else "gaming"
            "trending_movies_and_shows" -> if (vn) "trailer phim mới" else "official trailer"
            else -> null
        }
    }

    suspend fun kiosk(id: String): Pair<PageResult<Entry>, MoreLoader<Entry>> {
        val query = fallbackQuery(id) ?: return kioskRaw(id)
        val direct = runCatching { kioskRaw(id) }.getOrNull()
        if (direct != null && direct.first.items.isNotEmpty()) return direct
        val r = search(query, Filter.VIDEOS)
        // Bỏ video đang phát trực tiếp – đã có tab Trực tiếp riêng
        val items = r.page.items.filter { it !is VideoItem || !it.isLive }
        return r.page.copy(items = items) to r.more
    }

    /**
     * Nội dung khởi đầu khi chưa có lịch sử xem: trộn các mục thịnh hành theo quốc gia đã chọn
     * (Trò chơi, Podcast, Âm nhạc, Trailer) – để lần đầu mở app đã thấy video tiếng Việt.
     */
    suspend fun starter(): List<VideoItem> = coroutineScope {
        val lists = listOf("trending_music", "trending_gaming", "trending_podcasts_episodes", "trending_movies_and_shows").map { id ->
            async { runCatching { kiosk(id).first.items.filterIsInstance<VideoItem>().filter { !it.isLive } }.getOrDefault(emptyList()) }
        }.awaitAll()
        val out = LinkedHashMap<String, VideoItem>()
        val maxLen = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) for (l in lists) l.getOrNull(i)?.let { out.putIfAbsent(it.url, it) }
        out.values.toList()
    }

    private suspend fun kioskRaw(id: String): Pair<PageResult<Entry>, MoreLoader<Entry>> = io {
        val url = service.kioskList.getListLinkHandlerFactoryByType(id).fromId(id).url
        val info = KioskInfo.getInfo(service, url)
        val more = MoreLoader<Entry> { page ->
            io {
                val p = KioskInfo.getMoreItems(service, url, page)
                PageResult<Entry>(p.items.map { it.toVideo() }, p.nextPage.takeIf { p.hasNextPage() })
            }
        }
        PageResult(info.relatedItems.map<StreamInfoItem, Entry> { it.toVideo() }, info.nextPage.takeIf { info.hasNextPage() }) to more
    }

    // ------------------------------------------------------------ bảng tin kênh đăng ký
    private val feedCache = ConcurrentHashMap<String, Pair<Long, List<VideoItem>>>()

    /** Video mới của một kênh: dùng RSS (nhanh, nhẹ); nếu lỗi thì đọc tab Video của kênh. */
    suspend fun channelFeed(channelUrl: String, maxAgeMs: Long = 15 * 60_000): List<VideoItem> = io {
        val now = System.currentTimeMillis()
        feedCache[channelUrl]?.let { (t, items) -> if (now - t < maxAgeMs) return@io items }
        val items = try {
            FeedInfo.getInfo(service, channelUrl).relatedItems.map { it.toVideo() }
        } catch (e: Exception) {
            val ch = ChannelInfo.getInfo(service, channelUrl)
            val tab = ch.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters } ?: throw e
            ChannelTabInfo.getInfo(service, tab).relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
        }
        feedCache[channelUrl] = now to items
        items
    }

    data class FeedResult(val items: List<VideoItem>, val failed: List<String>)

    suspend fun subscriptionFeed(
        channels: List<String>,
        force: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): FeedResult = coroutineScope {
        val sem = Semaphore(6)
        var done = 0
        val failed = mutableListOf<String>()
        val lists = channels.map { url ->
            async {
                sem.withPermit {
                    val r = runCatching { channelFeed(url, if (force) 0 else 15 * 60_000) }
                    synchronized(failed) {
                        done++
                        if (r.isFailure) failed += url
                    }
                    onProgress(done, channels.size)
                    r.getOrDefault(emptyList())
                }
            }
        }.awaitAll()
        val all = lists.flatten().distinctBy { it.url }.sortedByDescending { it.uploadedAt }
        FeedResult(all, failed)
    }

    // ------------------------------------------------------------ đề xuất (tính trên máy)
    /**
     * Đề xuất được tạo hoàn toàn trên máy: lấy "video liên quan" của những video bạn xem gần đây
     * (và video mới của kênh đăng ký), rồi trộn lại. Không gửi lịch sử của bạn đi đâu cả.
     */
    suspend fun recommendations(seeds: List<String>, exclude: Set<String>): List<VideoItem> = coroutineScope {
        val sem = Semaphore(3)
        val lists = seeds.distinct().take(6).map { url ->
            async {
                sem.withPermit {
                    runCatching { stream(url, useCache = true) }.getOrNull()
                        ?.relatedItems?.filterIsInstance<StreamInfoItem>()?.map { it.toVideo() }
                        ?: emptyList()
                }
            }
        }.awaitAll()
        // Xen kẽ: lần lượt lấy 1 video từ mỗi danh sách để đề xuất đa dạng
        val out = LinkedHashMap<String, VideoItem>()
        val maxLen = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) for (l in lists) {
            val v = l.getOrNull(i) ?: continue
            if (v.url !in exclude && v.url !in seeds) out.putIfAbsent(v.url, v)
        }
        out.values.toList()
    }

    // ------------------------------------------------------------ liên kết
    enum class LinkKind { VIDEO, CHANNEL, PLAYLIST, NONE }

    fun linkKind(url: String): LinkKind = runCatching {
        when (service.getLinkTypeByUrl(url)) {
            StreamingService.LinkType.STREAM -> LinkKind.VIDEO
            StreamingService.LinkType.CHANNEL -> LinkKind.CHANNEL
            StreamingService.LinkType.PLAYLIST -> LinkKind.PLAYLIST
            else -> LinkKind.NONE
        }
    }.getOrDefault(LinkKind.NONE)

    fun normalizeVideoUrl(url: String): String = runCatching { service.streamLHFactory.fromUrl(url).url }.getOrDefault(url)

    // ------------------------------------------------------------ thông báo lỗi dễ hiểu
    fun friendlyError(e: Throwable): String = when (e) {
        is AgeRestrictedContentException -> "Video bị giới hạn độ tuổi, cần đăng nhập YouTube để xem."
        is GeographicRestrictionException -> "Video không khả dụng ở quốc gia của bạn."
        is PrivateContentException -> "Video này ở chế độ riêng tư."
        is PaidContentException -> "Đây là nội dung trả phí."
        is UnsupportedContentInCountryException -> "Mục này không có ở quốc gia đã chọn."
        is ReCaptchaException -> "YouTube đang tạm giới hạn mạng của bạn. Hãy thử lại sau ít phút hoặc đổi mạng."
        is ContentNotAvailableException -> e.message?.takeIf { it.isNotBlank() } ?: "Nội dung không khả dụng."
        is IOException -> "Không kết nối được. Kiểm tra mạng rồi thử lại."
        else -> "Có lỗi xảy ra: " + (e.message ?: e.javaClass.simpleName)
    }
}
