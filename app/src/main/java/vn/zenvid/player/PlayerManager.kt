package vn.zenvid.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import vn.zenvid.Services
import vn.zenvid.data.HttpDownloader
import vn.zenvid.data.VideoItem
import vn.zenvid.data.YouTube
import vn.zenvid.data.bestUrl
import vn.zenvid.data.toVideo
import vn.zenvid.data.DownloadEntry
import vn.zenvid.download.Downloads
import androidx.media3.datasource.DefaultDataSource

/** Trình phát dùng chung cho cả app (màn hình video, trình phát thu nhỏ, phát nền, PiP). */
@OptIn(UnstableApi::class)
object PlayerManager {

    sealed interface State {
        data object Idle : State
        data class Loading(val url: String, val preview: VideoItem?) : State
        data class Ready(val info: StreamInfo, val video: VideoItem, val qualities: List<Int>, val height: Int) : State
        data class Error(val url: String, val message: String, val preview: VideoItem?) : State
        /** Phát file đã tải về máy (không cần mạng). */
        data class Local(val video: VideoItem, val download: DownloadEntry) : State
    }

    /** Chế độ "Nghe ẩn": chỉ phát âm thanh, tiếp tục khi rời app / tắt màn hình. */
    private val _audioOnly = MutableStateFlow(false)
    val audioOnly: StateFlow<Boolean> = _audioOnly

    /** File đã tải đang được dùng làm nguồn phát (nếu có). */
    private var playingLocal: DownloadEntry? = null

    /** Với file trên máy: tắt/bật luồng hình bằng cách bỏ chọn track (không cần nạp lại). */
    private fun applyVideoTrack() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, _audioOnly.value)
            .build()
    }

    /** Bật/tắt Nghe ẩn cho thứ đang phát. */
    fun setAudioOnly(on: Boolean) {
        if (_audioOnly.value == on) return
        _audioOnly.value = on
        applyVideoTrack()
        val s = _state.value as? State.Ready ?: return
        if (playingLocal != null) return // file trên máy: đã xử lý bằng track
        // Nguồn hiện tại đã có hình (đang xem rồi chuyển sang nghe, hoặc ngược lại): chỉ ẩn/hiện luồng hình,
        // âm thanh chạy liên tục, không tải lại.
        val p = currentPick
        if (p == null || p.video != null || p.muxed != null || p.hls != null || p.dash != null) return
        // Chỉ khi bắt đầu bằng "Nghe ẩn" (nguồn chỉ có âm thanh) rồi muốn xem hình mới phải nạp thêm luồng hình
        val pos = player.currentPosition
        val keepPlaying = player.playWhenReady
        val (source, h) = runCatching { buildSource(s.info, preferredHeight(), audioOnlySource = false) }.getOrNull() ?: return
        player.setMediaSource(source, pos)
        player.prepare()
        player.playWhenReady = keepPlaying
        _state.value = s.copy(height = h)
    }

    /** Tỉ lệ khung hình của video đang phát (rộng/cao), 0 nếu chưa biết. < 1 là video dọc. */
    private val _videoAspect = MutableStateFlow(0f)
    val videoAspect: StateFlow<Float> = _videoAspect

    /** Video đang mở (trực tuyến hoặc đã tải). */
    private fun currentVideo(): VideoItem? = when (val s = _state.value) {
        is State.Ready -> s.video
        is State.Local -> s.video
        else -> null
    }

    lateinit var player: ExoPlayer
        private set
    private lateinit var app: Context

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** Hàng đợi (khi phát cả danh sách phát). */
    private val _queue = MutableStateFlow<List<VideoItem>>(emptyList())
    val queue: StateFlow<List<VideoItem>> = _queue
    private var queueIndex = -1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private val recentlyPlayed = ArrayDeque<String>()

    /** Các "máy khách" YouTube đã từ chối luồng của video hiện tại – lần sau chọn luồng khác. */
    private val blockedClients = mutableSetOf<String>()
    private var currentPick: Picked? = null

    private val prefs get() = Services.prefs
    private val library get() = Services.library

    fun init(context: Context) {
        app = context.applicationContext
        player = ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Giữ CPU + Wi‑Fi thức khi đang phát: không có dòng này, tắt màn hình là mạng ngủ và video đứng xoay vòng
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Đệm trước nhiều hơn (tới 2 phút) để mạng chập chờn không làm gián đoạn
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 120_000, 1_500, 3_000)
                    .build()
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    _videoAspect.value = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveProgress()
            }

            override fun onPlayerError(error: PlaybackException) {
                (_state.value as? State.Local)?.let { l ->
                    _state.value = State.Error(l.video.url, "Không mở được file đã tải (có thể đã bị xoá khỏi máy).", l.video)
                    return
                }
                val s = _state.value as? State.Ready ?: return
                // YouTube từ chối luồng (403…) -> thử luồng của "máy khách" khác trước khi báo lỗi
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                    val wasAudioOnly = currentPick?.let { it.video == null && it.muxed == null && it.hls == null && it.dash == null } ?: false
                    currentPick?.let { blockedClients += StreamPicker.clientsOf(it) }
                    val pos = player.currentPosition
                    val rebuilt = runCatching { buildSource(s.info, s.height.takeIf { it > 0 } ?: preferredHeight(), wasAudioOnly) }.getOrNull()
                    if (rebuilt != null) {
                        player.setMediaSource(rebuilt.first, pos)
                        player.prepare()
                        player.playWhenReady = true
                        _state.value = s.copy(height = rebuilt.second)
                        return
                    }
                }
                _state.value = State.Error(
                    s.video.url,
                    if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
                        "YouTube đang chặn luồng video này với mạng hiện tại. Thử lại sau, hoặc đổi mạng (Wi‑Fi/4G)."
                    else "Không phát được video (${error.errorCodeName}).",
                    s.video,
                )
            }
        })

        // Lưu vị trí đang xem định kỳ để lần sau xem tiếp
        scope.launch {
            while (true) {
                delay(5_000)
                if (player.isPlaying) saveProgress()
            }
        }
    }

    // ------------------------------------------------------------------ nguồn dữ liệu
    private val dataSourceFactory: DataSource.Factory by lazy {
        val ok = OkHttpDataSource.Factory(Services.http)
        ResolvingDataSource.Factory(ok) { spec ->
            spec.withAdditionalHeaders(mapOf("User-Agent" to HttpDownloader.userAgentForStream(spec.uri.toString())))
        }
    }

    private fun localSource(d: DownloadEntry, video: VideoItem): MediaSource =
        ProgressiveMediaSource.Factory(DefaultDataSource.Factory(app)).createMediaSource(
            MediaItem.Builder().setUri(d.fileUri).setMediaId(d.url)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(video.title).setArtist(video.uploader).setDisplayTitle(video.title)
                        .apply { video.thumbnail?.let { setArtworkUri(Uri.parse(it)) } }.build()
                ).build()
        )

    /** Mở file đã tải (dùng được khi không có mạng). */
    fun openLocal(d: DownloadEntry, audioOnly: Boolean = false) {
        if ((_state.value as? State.Local)?.download?.id == d.id) { setAudioOnly(audioOnly); return }
        _audioOnly.value = audioOnly
        saveProgress()
        loadJob?.cancel()
        _queue.value = emptyList(); queueIndex = -1
        val video = d.toItem()
        loadJob = scope.launch {
            val start = if (prefs.resume.value) library.positionOf(d.url).let { if (d.durationSec > 0 && it > d.durationSec * 1000 - 10_000) 0 else it } else 0L
            playLocal(d, video, start)
        }
    }

    private suspend fun playLocal(d: DownloadEntry, video: VideoItem, start: Long) {
        _videoAspect.value = 0f
        playingLocal = d
        applyVideoTrack()
        player.setMediaSource(localSource(d, video), start)
        player.prepare()
        player.playWhenReady = true
        _state.value = State.Local(video, d)
        library.recordWatch(video, start)
    }

    private fun progressive(item: MediaItem): MediaSource =
        ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item)

    private fun metadata(info: StreamInfo) = MediaMetadata.Builder()
        .setTitle(info.name)
        .setArtist(info.uploaderName)
        .setDisplayTitle(info.name)
        .apply { info.thumbnails.bestUrl(480)?.let { setArtworkUri(Uri.parse(it)) } }
        .build()

    private fun buildSource(info: StreamInfo, maxHeight: Int, audioOnlySource: Boolean = _audioOnly.value): Pair<MediaSource, Int> {
        if (audioOnlySource) {
            // Nghe ẩn: chỉ tải luồng âm thanh – đỡ tốn dữ liệu và pin
            StreamPicker.bestAudio(info.audioStreams, preferAac = true, blocked = blockedClients)?.let { a ->
                currentPick = Picked(audio = a)
                val item = MediaItem.Builder().setMediaId(info.url).setMediaMetadata(metadata(info)).setUri(a.content).build()
                return progressive(item) to maxHeight
            }
        }
        val p = StreamPicker.pick(info, maxHeight, prefs.preferAvc.value, blockedClients)
        currentPick = p
        val base = MediaItem.Builder().setMediaId(info.url).setMediaMetadata(metadata(info))

        val sources = mutableListOf<MediaSource>()
        when {
            p.hls != null -> sources += HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(base.setUri(p.hls).setMimeType(MimeTypes.APPLICATION_M3U8).build())
            p.dash != null -> sources += DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(base.setUri(p.dash).setMimeType(MimeTypes.APPLICATION_MPD).build())
            p.video != null && p.audio != null -> {
                sources += progressive(base.setUri(p.video.content).build())
                sources += progressive(MediaItem.fromUri(p.audio.content))
            }
            p.muxed != null -> sources += progressive(base.setUri(p.muxed.content).build())
            p.audio != null -> sources += progressive(base.setUri(p.audio.content).build())
        }

        if (prefs.subtitles.value && p.hls == null) sources += subtitleSources(info)

        val source = if (sources.size == 1) sources[0] else MergingMediaSource(*sources.toTypedArray())
        return source to p.height
    }

    /** Phụ đề (tắt mặc định trong Cài đặt). Bật lên thì chọn bằng nút "CC" trên trình phát. */
    private fun subtitleSources(info: StreamInfo): List<MediaSource> {
        val parserFactory = DefaultSubtitleParserFactory()
        val wanted = setOf("vi", "en")
        return info.subtitles
            .filter { it.format == MediaFormat.VTT || it.format == MediaFormat.TTML }
            .filter { !it.isAutoGenerated || it.languageTag.substringBefore('-') in wanted }
            .groupBy { it.languageTag to it.isAutoGenerated }
            .map { (_, list) -> list.firstOrNull { it.format == MediaFormat.VTT } ?: list.first() }
            .take(8)
            .map { s ->
                val mime = if (s.format == MediaFormat.VTT) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_TTML
                val label = s.displayLanguageName + if (s.isAutoGenerated) " (tự động)" else ""
                val format = Format.Builder().setSampleMimeType(mime).setLanguage(s.languageTag).setLabel(label).build()
                val extractors = ExtractorsFactory { arrayOf(SubtitleExtractor(parserFactory.create(format), format)) }
                ProgressiveMediaSource.Factory(dataSourceFactory, extractors).createMediaSource(MediaItem.fromUri(s.content))
            }
    }

    private fun preferredHeight(): Int {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val unmetered = caps?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } ?: false
        return if (unmetered) prefs.qualityWifi.value else prefs.qualityMobile.value
    }

    // ------------------------------------------------------------------ điều khiển
    /** Mở một video. Nếu đang phát đúng video đó thì giữ nguyên. */
    fun open(rawUrl: String, preview: VideoItem? = null, queue: List<VideoItem>? = null, startMs: Long? = null, audioOnly: Boolean = false) {
        val url = YouTube.normalizeVideoUrl(rawUrl)
        val same = when (val s = _state.value) {
            is State.Ready -> s.video.url == url
            is State.Loading -> s.url == url
            is State.Local -> s.video.url == url
            else -> false
        }
        // Đang phát đúng video này: chỉ đổi chế độ nghe/xem. Video khác: bắt đầu theo chế độ được yêu cầu.
        if (same) { if (audioOnly) setAudioOnly(true) } else _audioOnly.value = audioOnly
        if (queue != null) {
            _queue.value = queue
            queueIndex = queue.indexOfFirst { it.url == url }
        } else if (_queue.value.none { it.url == url }) {
            _queue.value = emptyList(); queueIndex = -1
        } else {
            queueIndex = _queue.value.indexOfFirst { it.url == url }
        }

        when (val s = _state.value) {
            is State.Ready -> if (s.video.url == url) return
            is State.Loading -> if (s.url == url) return
            is State.Local -> if (s.video.url == url) return
            else -> {}
        }
        load(url, preview, startMs)
    }

    private fun load(url: String, preview: VideoItem?, startMs: Long?) {
        saveProgress()
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.value = State.Loading(url, preview)
        _videoAspect.value = if (preview?.isShort == true) 9f / 16f else 0f
        playingLocal = null
        applyVideoTrack()
        blockedClients.clear()
        currentPick = null
        recentlyPlayed.addLast(url)
        while (recentlyPlayed.size > 30) recentlyPlayed.removeFirst()

        loadJob = scope.launch {
            // Đã tải về máy: dùng file trên máy (đỡ tốn mạng), vẫn lấy thông tin trực tuyến nếu có mạng
            val local = Downloads.doneFor(url)
            try {
                val info = try {
                    YouTube.stream(url)
                } catch (e: java.io.IOException) {
                    if (local != null) {
                        val v = preview ?: local.toItem()
                        playLocal(local, v, startMs ?: if (prefs.resume.value) library.positionOf(url) else 0L)
                        return@launch
                    }
                    throw e
                }
                val video = info.toVideo()
                val durationMs = info.duration * 1000
                var start = startMs ?: if (prefs.resume.value) library.positionOf(info.url) else 0L
                if (durationMs > 0 && start > durationMs - 10_000) start = 0 // đã xem hết thì xem lại từ đầu
                if (info.startPosition > 0 && startMs == null) start = info.startPosition * 1000

                if (local != null) {
                    playingLocal = local
                    applyVideoTrack()
                    player.setMediaSource(localSource(local, video), start)
                    player.prepare()
                    player.playWhenReady = true
                    _state.value = State.Ready(info, video, emptyList(), local.actualHeight)
                    library.recordWatch(video, start)
                    return@launch
                }
                val (source, height) = buildSource(info, preferredHeight())
                player.setMediaSource(source, start)
                player.prepare()
                player.playWhenReady = true
                _state.value = State.Ready(info, video, StreamPicker.qualities(info), height)
                library.recordWatch(video, start)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = State.Error(url, YouTube.friendlyError(e), preview)
            }
        }
    }

    fun retry() {
        when (val s = _state.value) {
            is State.Error -> {
                val pos = player.currentPosition
                _state.value = State.Idle
                load(s.url, s.preview, pos.takeIf { it > 0 })
            }
            is State.Ready -> { val pos = player.currentPosition; _state.value = State.Idle; load(s.video.url, s.video, pos) }
            is State.Local -> { player.prepare(); player.play() }
            else -> {}
        }
    }

    fun setQuality(height: Int) {
        val s = _state.value as? State.Ready ?: return
        val pos = player.currentPosition
        val (source, h) = buildSource(s.info, height, audioOnlySource = false)
        player.setMediaSource(source, pos)
        player.prepare()
        player.playWhenReady = true
        _state.value = s.copy(height = h)
    }

    /** Bật/tắt phụ đề thì nạp lại nguồn tại vị trí hiện tại. */
    fun reloadSource() {
        val s = _state.value as? State.Ready ?: return
        setQuality(s.height.takeIf { it > 0 } ?: preferredHeight())
    }

    fun close() {
        _audioOnly.value = false
        playingLocal = null
        applyVideoTrack()
        saveProgress()
        loadJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _queue.value = emptyList(); queueIndex = -1
        _state.value = State.Idle
    }

    fun next(): Boolean {
        val q = _queue.value
        if (queueIndex >= 0 && queueIndex + 1 < q.size) {
            val v = q[queueIndex + 1]
            queueIndex++
            load(v.url, v, null)
            return true
        }
        val s = _state.value as? State.Ready ?: return false
        val related = s.info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
        val v = related.firstOrNull { it.url !in recentlyPlayed && !it.isLive } ?: return false
        load(v.url, v, 0)
        return true
    }

    fun hasQueueNext() = queueIndex >= 0 && queueIndex + 1 < _queue.value.size

    private fun onEnded() {
        val v = currentVideo() ?: return
        scope.launch { library.updatePosition(v.url, player.duration.coerceAtLeast(0)) }
        if (_state.value is State.Local) return
        if (hasQueueNext() || prefs.autoplay.value) next()
    }

    private fun saveProgress() {
        val v = currentVideo() ?: return
        val pos = player.currentPosition
        if (pos <= 0) return
        scope.launch { library.updatePosition(v.url, pos) }
    }
}
