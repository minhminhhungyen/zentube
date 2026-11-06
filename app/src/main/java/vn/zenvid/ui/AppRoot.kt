package vn.zenvid.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import vn.zenvid.MainActivity
import vn.zenvid.Services
import vn.zenvid.data.Profile
import vn.zenvid.data.VideoItem
import vn.zenvid.data.YouTube
import vn.zenvid.player.PlayerManager
import vn.zenvid.ui.components.Actions
import vn.zenvid.ui.components.CenterLoading
import vn.zenvid.ui.components.LocalActions
import vn.zenvid.ui.components.LocalProgress
import vn.zenvid.ui.screens.AccountPickerScreen
import vn.zenvid.ui.screens.ChannelScreen
import vn.zenvid.ui.screens.CreateProfileScreen
import vn.zenvid.ui.screens.HomeScreen
import vn.zenvid.ui.screens.LibraryScreen
import vn.zenvid.ui.screens.LocalPlaylistScreen
import vn.zenvid.ui.screens.PinScreen
import vn.zenvid.ui.screens.PlaylistScreen
import vn.zenvid.ui.screens.SavedListScreen
import vn.zenvid.ui.screens.SearchScreen
import vn.zenvid.ui.screens.SettingsScreen
import vn.zenvid.ui.screens.SubscriptionsScreen
import vn.zenvid.ui.screens.VideoScreen
import vn.zenvid.ui.theme.Brand

/** Điểm vào giao diện: chọn/đăng nhập tài khoản trước, rồi mới tới app chính. */
@Composable
fun AppRoot(activity: MainActivity) {
    val lib = Services.library
    val prefs = Services.prefs
    val profiles by lib.profiles.collectAsState(initial = null)
    val profileId by prefs.profileId.flow.collectAsStateWithLifecycle()
    var unlockedId by rememberSaveable { mutableStateOf(-1L) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    // Người dùng tự bấm "Chuyển tài khoản" -> hiện danh sách thay vì tự vào lại
    var pickerRequested by rememberSaveable { mutableStateOf(false) }

    val list = profiles ?: run { CenterLoading(); return }
    val current = list.firstOrNull { it.id == profileId }

    fun login(p: Profile) {
        prefs.profileId.value = p.id
        unlockedId = p.id
        pinFor = null
        pickerRequested = false
    }

    // Dùng ngay không cần đăng nhập: lần đầu mở app tự tạo một hồ sơ trên máy để lưu lịch sử, Xem sau…
    if (list.isEmpty() && !adding) {
        LaunchedEffect(Unit) {
            val id = lib.createProfile("Bạn", null)
            prefs.profileId.value = id
            unlockedId = id
        }
        CenterLoading()
        return
    }
    // Chỉ có một hồ sơ, không đặt PIN: vào thẳng app
    val only = list.singleOrNull()
    if (current == null && !adding && !pickerRequested && only != null && only.pinHash == null) {
        LaunchedEffect(only.id) { login(only) }
        CenterLoading()
        return
    }

    when {
        adding -> CreateProfileScreen(
            first = false,
            onCancel = { adding = false },
            onCreated = { id -> adding = false; pickerRequested = false; prefs.profileId.value = id; unlockedId = id },
        )
        pinFor != null -> PinScreen(pinFor!!, onBack = { pinFor = null }, onSuccess = { login(pinFor!!) })
        current == null -> AccountPickerScreen(list, onPick = { p -> if (p.pinHash != null) pinFor = p else login(p) }, onAdd = { adding = true })
        current.pinHash != null && unlockedId != current.id -> PinScreen(current, onBack = { pickerRequested = true; prefs.profileId.value = -1 }, onSuccess = { login(current) })
        else -> MainApp(activity, current, onSwitchAccount = {
            PlayerManager.close()
            pickerRequested = true
            prefs.profileId.value = -1
            unlockedId = -1
        })
    }
}

private object Routes {
    const val HOME = "home"
    const val SUBS = "subs"
    const val LIBRARY = "library"
    const val SEARCH = "search?q={q}"
    const val VIDEO = "video"
    const val CHANNEL = "channel?url={url}"
    const val PLAYLIST = "playlist?url={url}"
    const val LOCAL = "local/{id}"
    const val LIST = "list/{type}"
    const val SETTINGS = "settings"
}

@Composable
private fun MainApp(activity: MainActivity, profile: Profile, onSwitchAccount: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val playerState by PlayerManager.state.collectAsStateWithLifecycle()
    val inPip by activity.inPip

    val progressList by Services.library.progress.collectAsState(emptyList())
    val progress = remember(progressList) {
        progressList.filter { it.durationSec > 0 }.associate { it.url to (it.positionMs / 1000f / it.durationSec) }
    }

    val actions = remember(nav) { NavActions(nav, activity) }
    SupportDialogs()

    // Mở liên kết YouTube từ app khác / thông báo trình phát
    val pending by activity.pendingLink.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val link = pending ?: return@LaunchedEffect
        activity.pendingLink.value = null
        if (link == MainActivity.OPEN_DOWNLOADS) {
            nav.navigate("list/downloads") { launchSingleTop = true }
        } else if (link == MainActivity.OPEN_PLAYER) {
            if (PlayerManager.state.value != PlayerManager.State.Idle) nav.navigate(Routes.VIDEO) { launchSingleTop = true }
        } else {
            actions.openLink(link)
        }
    }

    DisposableEffect(route) {
        activity.onVideoScreen = route == Routes.VIDEO
        onDispose { }
    }

    val showBottom = route in setOf(Routes.HOME, Routes.SUBS, Routes.LIBRARY)
    val showMini = route != Routes.VIDEO && playerState != PlayerManager.State.Idle && !inPip

    CompositionLocalProvider(LocalActions provides actions, LocalProgress provides progress) {
        if (inPip) {
            // Chế độ cửa sổ nổi: chỉ hiện video
            VideoScreen(pipMode = true)
            return@CompositionLocalProvider
        }
        Scaffold(
            bottomBar = {
                Column(if (showBottom) Modifier else Modifier.navigationBarsPadding()) {
                    if (showMini) MiniPlayer(playerState, onOpen = { nav.navigate(Routes.VIDEO) { launchSingleTop = true } })
                    if (showBottom) BottomBar(nav, route)
                }
            },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        ) { padding ->
            val navPad = if (showBottom || showMini || route == Routes.VIDEO) Modifier else Modifier.navigationBarsPadding()
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding).then(navPad).fillMaxSize()) {
                composable(Routes.HOME) { HomeScreen(profile) }
                composable(Routes.SUBS) { SubscriptionsScreen(profile) }
                composable(Routes.LIBRARY) { LibraryScreen(profile, onSwitchAccount) }
                composable(Routes.SEARCH, arguments = listOf(navArgument("q") { type = NavType.StringType; nullable = true })) {
                    SearchScreen(it.arguments?.getString("q"))
                }
                composable(Routes.VIDEO) { VideoScreen(pipMode = false) }
                composable(Routes.CHANNEL, arguments = listOf(navArgument("url") { type = NavType.StringType })) {
                    ChannelScreen(it.arguments?.getString("url") ?: "")
                }
                composable(Routes.PLAYLIST, arguments = listOf(navArgument("url") { type = NavType.StringType })) {
                    PlaylistScreen(it.arguments?.getString("url") ?: "")
                }
                composable(Routes.LOCAL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    LocalPlaylistScreen(it.arguments?.getLong("id") ?: 0)
                }
                composable(Routes.LIST, arguments = listOf(navArgument("type") { type = NavType.StringType })) {
                    val type = it.arguments?.getString("type") ?: "history"
                    if (type == "downloads") vn.zenvid.ui.screens.DownloadsScreen() else SavedListScreen(type)
                }
                composable(Routes.SETTINGS) { SettingsScreen(profile, onSwitchAccount) }
            }
        }
    }
}

private class NavActions(private val nav: NavHostController, private val activity: MainActivity) : Actions {
    private fun enc(s: String) = Uri.encode(s)

    override fun openVideo(v: VideoItem, queue: List<VideoItem>?) {
        activity.askNotificationPermission()
        PlayerManager.open(v.url, v, queue)
        nav.navigate(Routes.VIDEO) { launchSingleTop = true }
    }

    override fun openPlayer() = nav.navigate(Routes.VIDEO) { launchSingleTop = true }

    override fun listen(v: VideoItem) {
        activity.askNotificationPermission()
        PlayerManager.open(v.url, v, audioOnly = true)
        android.widget.Toast.makeText(activity, "Đang nghe ẩn: ${v.title}", android.widget.Toast.LENGTH_SHORT).show()
    }
    override fun openChannel(url: String) = nav.navigate("channel?url=${enc(url)}")
    override fun openPlaylist(url: String) = nav.navigate("playlist?url=${enc(url)}")
    override fun openLocalPlaylist(id: Long) = nav.navigate("local/$id")
    override fun openList(type: String) = nav.navigate("list/$type")
    override fun openSettings() = nav.navigate(Routes.SETTINGS)
    override fun openAccounts() = nav.navigate(Routes.SETTINGS)

    override fun openSearch(query: String?) {
        nav.navigate(if (query == null) "search" else "search?q=${enc(query)}") { launchSingleTop = true }
    }

    override fun openLink(url: String) {
        when (YouTube.linkKind(url)) {
            YouTube.LinkKind.VIDEO -> {
                val start = Regex("""[?&#]t=(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()
                activity.askNotificationPermission()
                PlayerManager.open(url, null, null, start?.times(1000))
                nav.navigate(Routes.VIDEO) { launchSingleTop = true }
            }
            YouTube.LinkKind.CHANNEL -> openChannel(url)
            YouTube.LinkKind.PLAYLIST -> openPlaylist(url)
            YouTube.LinkKind.NONE -> runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
            }
        }
    }

    override fun back() {
        if (!nav.popBackStack()) activity.moveTaskToBack(true)
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    data class Tab(val route: String, val label: String, val on: androidx.compose.ui.graphics.vector.ImageVector, val off: androidx.compose.ui.graphics.vector.ImageVector)
    val tabs = listOf(
        Tab(Routes.HOME, "Trang chủ", Icons.Filled.Home, Icons.Outlined.Home),
        Tab(Routes.SUBS, "Kênh đăng ký", Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions),
        Tab(Routes.LIBRARY, "Bạn", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        tabs.forEach { t ->
            val selected = route == t.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) nav.navigate(t.route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(if (selected) t.on else t.off, null) },
                label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/** Trình phát thu nhỏ phía dưới khi rời màn hình video – video vẫn tiếp tục phát. */
@Composable
private fun MiniPlayer(state: PlayerManager.State, onOpen: () -> Unit) {
    val player = PlayerManager.player
    var playing by remember { mutableStateOf(player.isPlaying || player.playWhenReady) }
    var fraction by remember { mutableStateOf(0f) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = player.playWhenReady }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playing = playWhenReady }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val d = player.duration
            fraction = if (d > 0) player.currentPosition.toFloat() / d else 0f
            delay(500)
        }
    }

    val audioOnly by PlayerManager.audioOnly.collectAsStateWithLifecycle()
    val (title, sub, thumb) = when (state) {
        is PlayerManager.State.Ready -> Triple(state.video.title, (if (audioOnly) "🎧 Nghe ẩn · " else "") + state.video.uploader, state.video.thumbnail)
        is PlayerManager.State.Local -> Triple(state.video.title, (if (audioOnly) "🎧 Nghe ẩn · " else "Đã tải · ") + state.video.uploader, state.video.thumbnail)
        is PlayerManager.State.Loading -> Triple(state.preview?.title ?: "Đang tải…", state.preview?.uploader ?: "", state.preview?.thumbnail)
        is PlayerManager.State.Error -> Triple(state.preview?.title ?: "Lỗi", state.message, state.preview?.thumbnail)
        PlayerManager.State.Idle -> return
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onOpen).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    thumb, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { if (player.playWhenReady) player.pause() else player.play() }) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Tạm dừng" else "Phát")
                }
                IconButton(onClick = { PlayerManager.close() }) { Icon(Icons.Filled.Close, "Đóng") }
            }
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = Brand, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}
