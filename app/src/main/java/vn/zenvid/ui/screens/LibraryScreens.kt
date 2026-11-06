package vn.zenvid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.launch
import vn.zenvid.Services
import vn.zenvid.data.HistoryEntry
import vn.zenvid.data.Lists
import vn.zenvid.data.PlaylistEntry
import vn.zenvid.data.Profile
import vn.zenvid.data.VideoItem
import vn.zenvid.data.relativeTime
import vn.zenvid.ui.components.Avatar
import vn.zenvid.ui.components.ConfirmDialog
import vn.zenvid.ui.components.LocalActions
import vn.zenvid.ui.components.MessageView
import vn.zenvid.ui.components.PlaylistRow
import vn.zenvid.ui.components.ProfileAvatar
import vn.zenvid.ui.components.SectionHeader
import vn.zenvid.ui.components.TextInputDialog
import vn.zenvid.ui.components.Thumbnail
import vn.zenvid.ui.components.VideoMenu
import vn.zenvid.ui.components.VideoRow
import vn.zenvid.ui.components.removeAction

/** Tab "Bạn": tài khoản, lịch sử, Xem sau, Đã thích, danh sách phát. */
@Composable
fun LibraryScreen(profile: Profile, onSwitchAccount: () -> Unit) {
    val lib = Services.library
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val history by lib.history.collectAsState(emptyList())
    val later by lib.saved(Lists.LATER).collectAsState(emptyList())
    val liked by lib.saved(Lists.LIKED).collectAsState(emptyList())
    val subs by lib.subscriptions.collectAsState(emptyList())
    val playlists by lib.playlists.collectAsState(emptyList())
    var creating by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile.name, profile.color, 64.dp)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Lưu trên thiết bị này · không cần đăng nhập",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { actions.openSearch() }) { Icon(Icons.Outlined.Search, "Tìm kiếm") }
                IconButton(onClick = { actions.openSettings() }) { Icon(Icons.Outlined.Settings, "Cài đặt") }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSwitchAccount) { Icon(Icons.Outlined.SwitchAccount, null); Text("  Thêm / chuyển người dùng") }
            }
        }

        // ---- Lịch sử (cuộn ngang như YouTube)
        item {
            SectionHeader("Lịch sử", if (history.isNotEmpty()) "Xem tất cả" else null) { actions.openList("history") }
            if (history.isEmpty()) {
                Text(
                    "Video bạn xem sẽ hiện ở đây.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(history.take(20), key = { it.url }) { h -> HistoryCard(h.toItem()) }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            NavRow(Icons.Outlined.History, "Lịch sử xem", "${history.size} video") { actions.openList("history") }
            NavRow(Icons.Outlined.WatchLater, "Xem sau", "${later.size} video") { actions.openList(Lists.LATER) }
            NavRow(Icons.Outlined.ThumbUp, "Video đã thích", "${liked.size} video") { actions.openList(Lists.LIKED) }
            val downloads by vn.zenvid.download.Downloads.all.collectAsState(emptyList())
            NavRow(Icons.Outlined.Download, "Video đã tải", "${downloads.count { it.status == vn.zenvid.download.Status.DONE }} video · xem không cần mạng") { actions.openList("downloads") }
            NavRow(Icons.Outlined.Subscriptions, "Kênh đã đăng ký", "${subs.size} kênh") { actions.openList("channels") }
        }

        item {
            SectionHeader("Danh sách phát", "Tạo mới") { creating = true }
        }
        if (playlists.isEmpty()) item {
            Text(
                "Tạo danh sách phát riêng để sắp xếp video. Mọi thứ chỉ lưu trên máy này.",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(playlists, key = { "pl_" + it.playlist.id }) { p ->
            PlaylistRow(
                PlaylistEntry("local:${p.playlist.id}", p.playlist.name, p.thumbnail, "Trên máy", p.count.toLong()),
                onClick = { actions.openLocalPlaylist(p.playlist.id) },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (creating) TextInputDialog("Danh sách phát mới", "Tên", "", confirm = "Tạo", onDismiss = { creating = false }) { name ->
        creating = false
        scope.launch { val id = lib.createPlaylist(name); actions.openLocalPlaylist(id) }
    }
}

@Composable
private fun HistoryCard(v: VideoItem) {
    val actions = LocalActions.current
    Column(Modifier.width(160.dp).clickable { actions.openVideo(v) }) {
        Thumbnail(v, Modifier.fillMaxWidth(), corner = 8.dp)
        Row {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text(v.uploader, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VideoMenu(v)
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Màn hình danh sách: lịch sử, Xem sau, Đã thích, kênh đã đăng ký. */
@Composable
fun SavedListScreen(type: String) {
    val lib = Services.library
    val prefs = Services.prefs
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val pauseHistory by prefs.pauseWatchHistory.flow.collectAsState()

    val title = when (type) {
        "history" -> "Lịch sử xem"
        Lists.LATER -> "Xem sau"
        Lists.LIKED -> "Video đã thích"
        else -> "Kênh đã đăng ký"
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (type == "history") Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Tuỳ chọn") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        { Text(if (pauseHistory) "Bật lại lịch sử xem" else "Tạm dừng lịch sử xem") },
                        { menu = false; prefs.pauseWatchHistory.value = !pauseHistory },
                        leadingIcon = { Icon(if (pauseHistory) Icons.Outlined.PlayCircle else Icons.Outlined.PauseCircle, null) },
                    )
                    DropdownMenuItem(
                        { Text("Xoá toàn bộ lịch sử xem") }, { menu = false; confirmClear = true },
                        leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                    )
                }
            }
        }

        if (type == "history" || type == "channels") {
            OutlinedTextField(
                filter, { filter = it }, placeholder = { Text("Tìm trong danh sách") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (type == "history" && pauseHistory) {
            Text(
                "Lịch sử xem đang tạm dừng – video bạn xem sẽ không được ghi lại.",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
            )
        }

        when (type) {
            "channels" -> {
                val subs by lib.subscriptions.collectAsState(emptyList())
                val shown = subs.filter { filter.isBlank() || it.name.contains(filter, true) }
                if (subs.isEmpty()) MessageView(Icons.Outlined.Subscriptions, "Bạn chưa đăng ký kênh nào.")
                else LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.channelUrl }) { s ->
                        var m by remember { mutableStateOf(false) }
                        Row(
                            Modifier.fillMaxWidth().clickable { actions.openChannel(s.channelUrl) }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(s.avatar, s.name, 44.dp)
                            Text(s.name, Modifier.weight(1f).padding(horizontal = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box {
                                IconButton(onClick = { m = true }) { Icon(Icons.Default.MoreVert, "Tuỳ chọn") }
                                DropdownMenu(m, { m = false }) {
                                    DropdownMenuItem({ Text("Huỷ đăng ký") }, {
                                        m = false; scope.launch { lib.setSubscribed(s.channelUrl, s.name, s.avatar, false) }
                                    })
                                }
                            }
                        }
                    }
                }
            }
            "history" -> {
                val history by lib.history.collectAsState(null)
                val list = history
                if (list != null && list.isEmpty()) MessageView(Icons.Outlined.History, "Chưa có lịch sử xem.")
                else LazyColumn(Modifier.fillMaxSize()) {
                    val shown = (list ?: emptyList()).filter { filter.isBlank() || it.title.contains(filter, true) || it.uploader.contains(filter, true) }
                    var lastDay = ""
                    shown.forEach { h: HistoryEntry ->
                        val day = dayLabel(h.watchedAt)
                        if (day != lastDay) {
                            lastDay = day
                            item(key = "d_$day") { SectionHeader(day) }
                        }
                        item(key = h.url) {
                            VideoRow(h.toItem(), extraMenu = listOf(removeAction("Xoá khỏi lịch sử xem") { scope.launch { lib.deleteHistory(h.url) } }))
                        }
                    }
                }
            }
            else -> {
                val saved by lib.saved(type).collectAsState(null)
                val list = saved
                val videos = list?.map { it.toItem() } ?: emptyList()
                if (list != null && list.isEmpty()) MessageView(
                    if (type == Lists.LATER) Icons.Outlined.WatchLater else Icons.Outlined.ThumbUp,
                    if (type == Lists.LATER) "Bấm ⋮ ở video rồi chọn \"Lưu vào Xem sau\"." else "Video bạn bấm Thích sẽ hiện ở đây.",
                )
                else LazyColumn(Modifier.fillMaxSize()) {
                    if (videos.isNotEmpty()) item {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            OutlinedButton(onClick = { actions.openVideo(videos.first(), videos) }) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null); Text("  Phát tất cả")
                            }
                        }
                    }
                    items(videos, key = { it.url }) { v ->
                        VideoRow(v, queue = videos, extraMenu = listOf(removeAction("Xoá khỏi danh sách") { scope.launch { lib.setSaved(type, v, false) } }))
                    }
                }
            }
        }
    }

    if (confirmClear) ConfirmDialog("Xoá lịch sử xem?", "Toàn bộ lịch sử xem của tài khoản này sẽ bị xoá khỏi máy.", "Xoá", { confirmClear = false }) {
        scope.launch { lib.clearHistory() }
    }
}

private fun dayLabel(ms: Long): String {
    val days = ((System.currentTimeMillis() - ms) / 86_400_000L)
    return when {
        days < 1 -> "Hôm nay"
        days < 2 -> "Hôm qua"
        days < 7 -> "Tuần này"
        else -> relativeTime(ms).replaceFirstChar { it.uppercase() }.let { if (it.contains("tuần")) "Tháng này" else it }
    }
}
