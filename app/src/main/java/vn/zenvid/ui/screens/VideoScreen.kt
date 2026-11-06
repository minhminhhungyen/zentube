package vn.zenvid.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.rememberUpdatedState
import vn.zenvid.player.GestureHint
import vn.zenvid.player.PlayerGestures
import vn.zenvid.player.PlayerViewRef
import vn.zenvid.player.FrameCapture
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.outlined.Download
import vn.zenvid.player.StreamPicker
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import vn.zenvid.MainActivity
import vn.zenvid.Services
import vn.zenvid.data.Lists
import vn.zenvid.data.VideoItem
import vn.zenvid.data.YouTube
import vn.zenvid.data.bestUrl
import vn.zenvid.data.compactNumber
import vn.zenvid.data.relativeTime
import vn.zenvid.data.subscribersText
import vn.zenvid.data.toVideo
import vn.zenvid.data.videoIdOf
import vn.zenvid.data.viewsText
import vn.zenvid.data.whenText
import vn.zenvid.player.PlayerManager
import vn.zenvid.ui.components.AddToPlaylistDialog
import vn.zenvid.ui.components.Avatar
import vn.zenvid.ui.components.LocalActions
import vn.zenvid.ui.components.OnNearEnd
import vn.zenvid.ui.components.PagedList
import vn.zenvid.ui.components.SmallLoading
import vn.zenvid.ui.components.SubscribeButton
import vn.zenvid.ui.components.VideoRow
import vn.zenvid.ui.components.shareUrl

@Composable
fun VideoScreen(pipMode: Boolean) {
    val state by PlayerManager.state.collectAsStateWithLifecycle()
    val actions = LocalActions.current
    val activity = LocalContext.current as Activity
    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (state == PlayerManager.State.Idle && !pipMode) {
        LaunchedEffect(Unit) { actions.back() }
        return
    }

    if (pipMode) {
        PlayerBox(state, Modifier.fillMaxSize(), controls = false, fullscreen = true, onFullscreen = {})
        return
    }

    val aspect by PlayerManager.videoAspect.collectAsStateWithLifecycle()
    val isShortState = when (val s = state) {
        is PlayerManager.State.Ready -> s.video.isShort
        is PlayerManager.State.Loading -> s.preview?.isShort == true
        else -> false
    }
    // Video dọc (Shorts, quay bằng điện thoại…): tỉ lệ < 1
    val vertical = if (aspect > 0f) aspect < 0.95f else isShortState

    // Toàn màn hình: video ngang thì xoay máy, video dọc thì giữ máy dọc và phóng hết màn hình
    var portraitFullscreen by rememberSaveable { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableStateOf(false) }
    val fullscreen = landscape || portraitFullscreen
    FullscreenSystemBars(fullscreen)
    DisposableEffect(Unit) {
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    LaunchedEffect(state is PlayerManager.State.Loading) { if (state is PlayerManager.State.Loading) zoom = false }
    fun toggleFullscreen(enter: Boolean) {
        if (vertical && !landscape) {
            portraitFullscreen = enter
            return
        }
        portraitFullscreen = false
        activity.requestedOrientation =
            if (enter) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    BackHandler(enabled = fullscreen) { toggleFullscreen(false) }

    if (fullscreen) {
        // Độ sáng chỉnh bằng cử chỉ chỉ áp dụng lúc xem toàn màn hình
        DisposableEffect(Unit) { onDispose { PlayerGestures.resetBrightness(activity) } }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerBox(
                state, Modifier.fillMaxSize(), controls = true, fullscreen = true, onFullscreen = ::toggleFullscreen,
                zoom = zoom, onZoom = { zoom = it },
            )
            IconButton(
                onClick = { takeScreenshot(activity) },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 64.dp)
                    .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
            ) { Icon(Icons.Outlined.PhotoCamera, "Chụp ảnh màn hình", tint = Color.White) }
            // Phóng to lấp đầy màn hình (cắt bớt viền) / vừa khung
            IconButton(
                onClick = { zoom = !zoom },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
            ) {
                Icon(if (zoom) Icons.Filled.ZoomInMap else Icons.Filled.ZoomOutMap, if (zoom) "Vừa khung" else "Phóng to lấp đầy", tint = Color.White)
            }
        }
        return
    }

    val screenH = config.screenHeightDp.dp
    val screenW = config.screenWidthDp.dp
    // Video ngang: theo đúng tỉ lệ (tối thiểu 16:9 cho gọn). Video dọc: cao tối đa ~70% màn hình cho to, rõ.
    val playerModifier = if (vertical) {
        val a = if (aspect > 0f) aspect else 9f / 16f
        Modifier.fillMaxWidth().height(minOf(screenW / a, screenH * 0.7f))
    } else {
        val a = if (aspect > 0f) aspect.coerceIn(16f / 9f, 2.4f) else 16f / 9f
        Modifier.fillMaxWidth().aspectRatio(a)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().animateContentSize()) {
            PlayerBox(state, playerModifier, controls = true, fullscreen = false, onFullscreen = ::toggleFullscreen)
        }
        when (val s = state) {
            is PlayerManager.State.Ready -> VideoDetails(s)
            is PlayerManager.State.Local -> LocalDetails(s)
            is PlayerManager.State.Loading -> s.preview?.let { PreviewHeader(it) }
            is PlayerManager.State.Error -> s.preview?.let { PreviewHeader(it) }
            PlayerManager.State.Idle -> {}
        }
    }
}

@Composable
private fun FullscreenSystemBars(fullscreen: Boolean) {
    val view = LocalView.current
    DisposableEffect(fullscreen) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerBox(
    state: PlayerManager.State,
    modifier: Modifier,
    controls: Boolean,
    fullscreen: Boolean,
    onFullscreen: (Boolean) -> Unit,
    zoom: Boolean = false,
    onZoom: (Boolean) -> Unit = {},
) {
    val actions = LocalActions.current
    val activity = LocalContext.current as Activity
    // Cử chỉ: lấy giá trị mới nhất mỗi lần chạm (view chỉ tạo một lần)
    var hint by remember { mutableStateOf<GestureHint?>(null) }
    val fsNow by rememberUpdatedState(fullscreen)
    val onFsNow by rememberUpdatedState(onFullscreen)
    val onZoomNow by rememberUpdatedState(onZoom)
    LaunchedEffect(hint) {
        val h = hint ?: return@LaunchedEffect
        if (h.kind != GestureHint.Kind.SPEED) { kotlinx.coroutines.delay(900); hint = null }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    PlayerViewRef.view = java.lang.ref.WeakReference(this)
                    if (controls) PlayerGestures(
                        activity, this,
                        isFullscreen = { fsNow },
                        onHint = { hint = it },
                        onSwipeUp = { if (!fsNow) onFsNow(true) },
                        onSwipeDown = { if (fsNow) onFsNow(false) else actions.back() },
                        onZoom = { onZoomNow(it) },
                    ).attach()
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    player = PlayerManager.player
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { v ->
                v.useController = controls
                v.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                if (v.player !== PlayerManager.player) v.player = PlayerManager.player
                if (controls) {
                    v.setFullscreenButtonClickListener { onFullscreen(it) }
                    v.setFullscreenButtonState(fullscreen)
                }
            },
            onRelease = { it.player = null; if (PlayerViewRef.get() === it) PlayerViewRef.view = null },
            modifier = Modifier.fillMaxSize(),
        )
        hint?.let { GestureHintView(it) }
        when (state) {
            is PlayerManager.State.Loading -> {
                AsyncImage(state.preview?.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                CircularProgressIndicator(color = Color.White)
            }
            is PlayerManager.State.Error -> Column(
                Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(state.message, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { PlayerManager.retry() }) { Text("Thử lại") }
            }
            else -> {}
        }
        val audioOnly by PlayerManager.audioOnly.collectAsState()
        if (audioOnly && (state is PlayerManager.State.Ready || state is PlayerManager.State.Local)) {
            val thumb = (state as? PlayerManager.State.Ready)?.video?.thumbnail ?: (state as? PlayerManager.State.Local)?.video?.thumbnail
            ListenOverlay(thumb, compact = !controls)
        }
        if (controls && !fullscreen) {
            IconButton(onClick = { actions.back() }, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Thu nhỏ", tint = Color.White)
            }
        }
    }
}

/** Màn hình khi đang Nghe ẩn: ảnh bìa + điều khiển, nút quay lại xem video. */
@Composable
private fun ListenOverlay(thumbnail: String?, compact: Boolean) {
    val player = PlayerManager.player
    var playing by remember { mutableStateOf(player.playWhenReady) }
    DisposableEffect(player) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playing = playWhenReady }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        if (compact) {
            Icon(Icons.Outlined.Headphones, null, tint = Color.White, modifier = Modifier.size(40.dp))
            return@Box
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Headphones, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("  Đang nghe ẩn", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.seekBack() }) { Icon(Icons.Filled.Replay10, "Lùi 10 giây", tint = Color.White) }
                IconButton(onClick = { if (player.playWhenReady) player.pause() else player.play() }, modifier = Modifier.size(64.dp)) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Tạm dừng" else "Phát", tint = Color.White, modifier = Modifier.size(48.dp))
                }
                IconButton(onClick = { player.seekForward() }) { Icon(Icons.Filled.Forward10, "Tới 10 giây", tint = Color.White) }
            }
            FilledTonalButton(onClick = { PlayerManager.setAudioOnly(false) }) {
                Icon(Icons.Outlined.Videocam, null, Modifier.size(18.dp)); Text("  Xem video")
            }
        }
    }
}

@Composable
private fun PreviewHeader(v: VideoItem) {
    Column(Modifier.padding(12.dp)) {
        Text(v.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(v.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ====================================================================== chi tiết video
class CommentsVm : ViewModel() {
    var url: String? = null; private set
    var count by mutableStateOf(-1); private set
    var disabled by mutableStateOf(false); private set
    var list: PagedList<CommentsInfoItem>? by mutableStateOf(null); private set

    fun load(videoUrl: String) {
        if (url == videoUrl) return
        url = videoUrl
        count = -1; disabled = false
        list = PagedList(viewModelScope, { it.commentId ?: it.url ?: it.hashCode() }) {
            val r = YouTube.comments(videoUrl)
            count = r.count; disabled = r.disabled
            r.page to r.more
        }.also { it.refresh() }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoDetails(s: PlayerManager.State.Ready) {
    val info = s.info
    val v = s.video
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lib = Services.library
    val prefs = Services.prefs

    val liked by remember(v.url) { lib.isSaved(Lists.LIKED, v.url) }.collectAsState(false)
    val later by remember(v.url) { lib.isSaved(Lists.LATER, v.url) }.collectAsState(false)
    val subscribed by remember(info.uploaderUrl) { lib.isSubscribed(info.uploaderUrl ?: "") }.collectAsState(false)
    val autoplay by prefs.autoplay.flow.collectAsState()
    val subtitles by prefs.subtitles.flow.collectAsState()
    val queue by PlayerManager.queue.collectAsState()
    val hideShorts by prefs.hideShorts.flow.collectAsState()

    var expanded by remember(v.url) { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(false) }
    val downloads by remember(v.url) { vn.zenvid.download.Downloads.forUrl(v.url) }.collectAsState(emptyList())

    val commentsVm: CommentsVm = viewModel()
    LaunchedEffect(v.url) { commentsVm.load(v.url) }

    val related = remember(info) {
        info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
    }.filter { !(hideShorts && it.isShort) }
    val listState = rememberLazyListState()
    LaunchedEffect(v.url) { listState.scrollToItem(0) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        // ---- tiêu đề
        item(key = "title") {
            Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp).animateContentSize()) {
                Text(
                    info.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(viewsText(info.viewCount, v.isLive), v.whenText(), if (!expanded) "...thêm" else null).joinToString("  "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (expanded) {
                    Spacer(Modifier.height(12.dp))
                    DescriptionText(info.description, v.url)
                    if (info.tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(info.tags.take(12).joinToString(" ") { "#$it" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // ---- kênh
        item(key = "channel") {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = info.uploaderUrl != null) { info.uploaderUrl?.let(actions::openChannel) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(info.uploaderAvatars.bestUrl(88), info.uploaderName ?: "", 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(info.uploaderName ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subscribersText(info.uploaderSubscriberCount)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (info.uploaderUrl != null) SubscribeButton(subscribed, onClick = {
                    scope.launch { lib.setSubscribed(info.uploaderUrl, info.uploaderName ?: "", info.uploaderAvatars.bestUrl(88), !subscribed) }
                })
            }
        }

        // ---- nút thao tác
        item(key = "actions") {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Pill(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, if (info.likeCount >= 0) compactNumber(info.likeCount) else "Thích") {
                        scope.launch { lib.setSaved(Lists.LIKED, v, !liked) }
                    }
                }
                item { Pill(Icons.Outlined.Share, "Chia sẻ") { shareUrl(ctx, v.url, v.title) } }
                item {
                    Pill(if (later) Icons.Filled.WatchLater else Icons.Outlined.WatchLater, if (later) "Đã lưu" else "Xem sau") {
                        scope.launch { lib.setSaved(Lists.LATER, v, !later) }
                    }
                }
                item { Pill(Icons.AutoMirrored.Filled.PlaylistAdd, "Lưu") { addToPlaylist = true } }
                item {
                    val isDone = downloads.any { it.status == vn.zenvid.download.Status.DONE }
                    val active = downloads.any { it.status == vn.zenvid.download.Status.QUEUED || it.status == vn.zenvid.download.Status.RUNNING }
                    Pill(
                        if (isDone) Icons.Filled.DownloadDone else Icons.Outlined.Download,
                        when { isDone -> "Đã tải"; active -> "Đang tải…"; else -> "Tải xuống" },
                    ) { download = true }
                }
                if (s.qualities.isNotEmpty()) item { Pill(Icons.Outlined.HighQuality, if (s.height > 0) "${s.height}p" else "Chất lượng") { showQuality = true } }
                item {
                    Pill(if (subtitles) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaption, if (subtitles) "Phụ đề: bật" else "Phụ đề: tắt") {
                        prefs.subtitles.value = !subtitles
                        PlayerManager.reloadSource()
                    }
                }
                item {
                    val listening by PlayerManager.audioOnly.collectAsState()
                    Pill(Icons.Outlined.Headphones, if (listening) "Đang nghe ẩn" else "Nghe ẩn") {
                        startListening(ctx)
                    }
                }
                item { Pill(Icons.Outlined.PhotoCamera, "Chụp ảnh") { takeScreenshot(ctx) } }
                item { Pill(Icons.Outlined.PictureInPictureAlt, "Cửa sổ nổi") { (ctx as? MainActivity)?.enterPip() } }
            }
        }

        // ---- bình luận
        item(key = "comments") {
            val list = commentsVm.list
            val first = list?.items?.firstOrNull()
            Surface(
                onClick = { if (first != null) showComments = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Text("Bình luận", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (commentsVm.count > 0) Text("  " + compactNumber(commentsVm.count.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    when {
                        commentsVm.disabled -> Text("Bình luận đã bị tắt.", style = MaterialTheme.typography.bodySmall)
                        first != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(first.uploaderAvatars.bestUrl(48), first.uploaderName ?: "", 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(htmlToText(first.commentText), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        list?.loading == true -> Text("Đang tải…", style = MaterialTheme.typography.bodySmall)
                        list?.error != null -> Text("Không tải được bình luận.", style = MaterialTheme.typography.bodySmall)
                        else -> Text("Chưa có bình luận.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // ---- hàng đợi (khi phát danh sách)
        val idx = queue.indexOfFirst { it.url == v.url }
        if (idx >= 0 && queue.size > 1) {
            item(key = "queue_h") {
                Text(
                    "Tiếp theo trong danh sách (${idx + 1}/${queue.size})",
                    Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                )
            }
            items(queue.drop(idx + 1).take(5), key = { "q_" + it.url }) { VideoRow(it, queue = queue) }
        }

        // ---- tự động phát + video liên quan
        item(key = "autoplay") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Video tiếp theo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Tự động phát", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Switch(checked = autoplay, onCheckedChange = { prefs.autoplay.value = it })
            }
        }
        items(related, key = { "r_" + it.url }) { VideoRow(it) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showComments) {
        ModalBottomSheet(onDismissRequest = { showComments = false }) {
            CommentsSheet(commentsVm)
        }
    }
    if (showQuality) QualityDialog(s) { showQuality = false }
    if (addToPlaylist) AddToPlaylistDialog(v) { addToPlaylist = false }
    if (download) DownloadDialog(v, StreamPicker.qualities(info)) { download = false }
}

@Composable
private fun Pill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun QualityDialog(s: PlayerManager.State.Ready, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chất lượng video") },
        text = {
            LazyColumn {
                items(s.qualities) { q ->
                    Row(
                        Modifier.fillMaxWidth().clickable { PlayerManager.setQuality(q); onDismiss() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = q == s.height, onClick = null, modifier = Modifier.padding(12.dp))
                        Text("${q}p" + when { q >= 2160 -> " 4K"; q >= 1440 -> " 2K"; q >= 720 -> " HD"; else -> "" })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}

@Composable
private fun CommentsSheet(vm: CommentsVm) {
    val list = vm.list ?: return
    val state = rememberLazyListState()
    state.OnNearEnd { list.loadMore() }
    Column(Modifier.fillMaxWidth()) {
        Text("Bình luận", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        HorizontalDivider(Modifier.padding(top = 8.dp))
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth()) {
            items(list.items, key = { it.commentId ?: it.hashCode() }) { c -> CommentRow(c) }
            if (list.hasMore || list.loading) item { SmallLoading() }
        }
    }
}

@Composable
private fun CommentRow(c: CommentsInfoItem) {
    val actions = LocalActions.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Avatar(
            c.uploaderAvatars.bestUrl(48), c.uploaderName ?: "", 32.dp,
            Modifier.clickable(enabled = !c.uploaderUrl.isNullOrBlank()) { c.uploaderUrl?.let(actions::openChannel) },
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            val time = c.uploadDate?.instant?.toEpochMilli()?.let { relativeTime(it) } ?: c.textualUploadDate
            Text(
                listOfNotNull(c.uploaderName, time, if (c.isPinned) "📌 Đã ghim" else null).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(htmlToText(c.commentText), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Outlined.ThumbUp, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(
                    c.textualLikeCount?.takeIf { it.isNotBlank() } ?: c.likeCount.takeIf { it > 0 }?.toLong()?.let(::compactNumber) ?: "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (c.replyCount > 0) {
                    Spacer(Modifier.width(16.dp))
                    Text("${c.replyCount} phản hồi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                if (c.isHeartedByUploader) { Spacer(Modifier.width(12.dp)); Text("❤", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

private fun htmlToText(d: Description?): AnnotatedString {
    if (d == null) return AnnotatedString("")
    return if (d.type() == Description.Type.HTML) AnnotatedString.fromHtml(d.content() ?: "") else AnnotatedString(d.content() ?: "")
}

/** Mô tả video có liên kết bấm được: mốc thời gian thì tua tới, link YouTube thì mở trong app. */
@Composable
private fun DescriptionText(d: Description?, videoUrl: String) {
    val actions = LocalActions.current
    val linkColor = MaterialTheme.colorScheme.secondary
    val text = remember(d) {
        val content = d?.content() ?: ""
        when (d?.type()) {
            Description.Type.HTML -> AnnotatedString.fromHtml(
                content,
                linkStyles = TextLinkStyles(SpanStyle(color = linkColor)),
                linkInteractionListener = { link ->
                    val url = (link as? LinkAnnotation.Url)?.url ?: return@fromHtml
                    handleDescriptionLink(url, videoUrl, actions::openLink)
                },
            )
            else -> AnnotatedString(content)
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

private fun handleDescriptionLink(url: String, currentUrl: String, openLink: (String) -> Unit) {
    val same = videoIdOf(url) != null && videoIdOf(url) == videoIdOf(currentUrl)
    val t = Regex("""[?&#]t=(\d+)(?:s)?""").find(url)?.groupValues?.get(1)?.toLongOrNull()
    if (same && t != null) {
        PlayerManager.player.seekTo(t * 1000)
        PlayerManager.player.play()
    } else {
        openLink(url)
    }
}

/** Bật Nghe ẩn rồi thu app xuống nền: âm thanh vẫn phát, điều khiển trên thanh thông báo / màn hình khoá. */
fun startListening(ctx: android.content.Context) {
    PlayerManager.setAudioOnly(true)
    (ctx as? MainActivity)?.let { a ->
        a.askNotificationPermission()
        android.widget.Toast.makeText(a, "Đang nghe ẩn – điều khiển trên thanh thông báo", android.widget.Toast.LENGTH_SHORT).show()
        a.moveTaskToBack(true)
    }
}

/** Thông báo giữa màn hình khi đang vuốt / chạm (âm lượng, độ sáng, tua…). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.GestureHintView(h: GestureHint) {
    val icon = when (h.kind) {
        GestureHint.Kind.SEEK_BACK -> Icons.Filled.FastRewind
        GestureHint.Kind.SEEK_FORWARD -> Icons.Filled.FastForward
        GestureHint.Kind.SCRUB -> Icons.Outlined.SwapHoriz
        GestureHint.Kind.VOLUME -> Icons.AutoMirrored.Outlined.VolumeUp
        GestureHint.Kind.BRIGHTNESS -> Icons.Outlined.BrightnessMedium
        GestureHint.Kind.SPEED -> Icons.Outlined.Speed
        GestureHint.Kind.ZOOM -> Icons.Outlined.ZoomOutMap
    }
    val align = when (h.kind) {
        GestureHint.Kind.SEEK_BACK -> Alignment.CenterStart
        GestureHint.Kind.SEEK_FORWARD -> Alignment.CenterEnd
        GestureHint.Kind.SPEED -> Alignment.TopCenter
        else -> Alignment.Center
    }
    Column(
        Modifier.align(align).padding(24.dp)
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(h.text, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        h.progress?.let { p ->
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { p }, modifier = Modifier.width(140.dp),
                color = vn.zenvid.ui.theme.Brand, trackColor = Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

/** Chụp khung hình video đang phát, lưu vào Pictures/Zenvid. */
fun takeScreenshot(ctx: android.content.Context) {
    val view = PlayerViewRef.get()
    val title = (PlayerManager.state.value as? PlayerManager.State.Ready)?.video?.title
        ?: (PlayerManager.state.value as? PlayerManager.State.Local)?.video?.title ?: "video"
    if (view == null || PlayerManager.audioOnly.value) {
        android.widget.Toast.makeText(ctx, "Không có hình để chụp (đang Nghe ẩn?)", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    kotlinx.coroutines.MainScope().launch {
        FrameCapture.capture(ctx, view, title)
            .onSuccess { android.widget.Toast.makeText(ctx, "Đã lưu ảnh vào Pictures/Zenvid", android.widget.Toast.LENGTH_SHORT).show() }
            .onFailure { android.widget.Toast.makeText(ctx, it.message ?: "Không chụp được", android.widget.Toast.LENGTH_SHORT).show() }
    }
}
