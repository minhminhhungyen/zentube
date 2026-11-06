package vn.zenvid.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import vn.zenvid.Services
import vn.zenvid.data.ChannelItem
import vn.zenvid.data.Lists
import vn.zenvid.data.PlaylistEntry
import vn.zenvid.data.VideoItem
import vn.zenvid.data.compactNumber
import vn.zenvid.data.durationText
import vn.zenvid.data.metaLine
import vn.zenvid.data.subscribersText
import vn.zenvid.ui.theme.Brand

/** Các thao tác điều hướng dùng chung cho mọi màn hình. */
interface Actions {
    fun openVideo(v: VideoItem, queue: List<VideoItem>? = null)
    /** Mở màn hình trình phát cho thứ đang phát (vd. file đã tải). */
    fun openPlayer()
    /** Nghe ẩn một video: chỉ phát âm thanh, ở lại màn hình hiện tại (trình phát thu nhỏ hiện ở dưới). */
    fun listen(v: VideoItem)
    fun openChannel(url: String)
    fun openPlaylist(url: String)
    fun openLocalPlaylist(id: Long)
    fun openList(type: String)
    fun openSearch(query: String? = null)
    fun openSettings()
    fun openAccounts()
    fun openLink(url: String)
    fun back()
}

val LocalActions = compositionLocalOf<Actions> { error("Actions chưa được cung cấp") }

/** Tiến độ đã xem (0..1) theo URL – để vẽ thanh đỏ dưới ảnh thu nhỏ như YouTube. */
val LocalProgress = compositionLocalOf<Map<String, Float>> { emptyMap() }

fun shareUrl(ctx: Context, url: String, title: String? = null) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    if (title != null) send.putExtra(Intent.EXTRA_SUBJECT, title)
    ctx.startActivity(Intent.createChooser(send, "Chia sẻ"))
}

// ====================================================================== ảnh
@Composable
fun Thumbnail(v: VideoItem, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val progress = LocalProgress.current[v.url]
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        val badge = if (v.isLive) "TRỰC TIẾP" else durationText(v.durationSec)
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(if (v.isLive) vn.zenvid.ui.theme.LiveRed else Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (progress != null && progress > 0.01f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.35f)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(Brand))
        }
    }
}

@Composable
fun Avatar(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text(name.trim().firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
fun ProfileAvatar(name: String, color: Int, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Color(color)), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.45f).sp)
    }
}

// ====================================================================== video
/** Thẻ video lớn (trang chủ, bảng tin đăng ký). */
@Composable
fun VideoCard(v: VideoItem, extraMenu: List<MenuAction> = emptyList(), queue: List<VideoItem>? = null) {
    val actions = LocalActions.current
    Column(Modifier.fillMaxWidth().clickable { actions.openVideo(v, queue) }.padding(bottom = 16.dp)) {
        Thumbnail(v, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        Row(Modifier.padding(start = 12.dp, top = 10.dp)) {
            Avatar(
                v.uploaderAvatar, v.uploader, 36.dp,
                Modifier.clickable(enabled = v.uploaderUrl != null) { v.uploaderUrl?.let(actions::openChannel) },
            )
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(v.metaLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            VideoMenu(v, extraMenu)
        }
    }
}

/** Dòng video gọn (kết quả tìm kiếm, video liên quan, thư viện). */
@Composable
fun VideoRow(v: VideoItem, extraMenu: List<MenuAction> = emptyList(), queue: List<VideoItem>? = null, showUploader: Boolean = true) {
    val actions = LocalActions.current
    Row(
        Modifier.fillMaxWidth().clickable { actions.openVideo(v, queue) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Thumbnail(v, Modifier.width(168.dp), corner = 8.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(v.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            if (showUploader && v.uploader.isNotBlank()) {
                Text(v.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            val meta = v.metaLine(includeUploader = false)
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        VideoMenu(v, extraMenu)
    }
}

@Composable
fun ChannelRow(c: ChannelItem) {
    val actions = LocalActions.current
    Row(
        Modifier.fillMaxWidth().clickable { actions.openChannel(c.url) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(168.dp), contentAlignment = Alignment.Center) { Avatar(c.avatar, c.name, 88.dp) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subscribersText(c.subscribers)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (c.videoCount > 0) Text("${compactNumber(c.videoCount)} video", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PlaylistRow(p: PlaylistEntry, onClick: (() -> Unit)? = null) {
    val actions = LocalActions.current
    Row(
        Modifier.fillMaxWidth().clickable { onClick?.invoke() ?: actions.openPlaylist(p.url) }.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(Modifier.width(168.dp)) {
            Box(
                Modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(p.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                Row(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(64.dp).background(Color.Black.copy(alpha = 0.65f)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Color.White)
                        if (p.count >= 0) Text(compactNumber(p.count), color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!p.uploader.isNullOrBlank()) Text(p.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Danh sách phát", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ====================================================================== menu ba chấm
data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun VideoMenu(v: VideoItem, extra: List<MenuAction> = emptyList()) {
    var open by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lib = Services.library
    val inLater by remember(v.url) { lib.isSaved(Lists.LATER, v.url) }.collectAsState(false)

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Tuỳ chọn", Modifier.size(20.dp)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (inLater) "Xoá khỏi Xem sau" else "Lưu vào Xem sau") },
                leadingIcon = { Icon(if (inLater) Icons.Filled.WatchLater else Icons.Outlined.WatchLater, null) },
                onClick = { open = false; scope.launch { lib.setSaved(Lists.LATER, v, !inLater) } },
            )
            DropdownMenuItem(
                text = { Text("Lưu vào danh sách phát") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                onClick = { open = false; addToPlaylist = true },
            )
            val acts = LocalActions.current
            DropdownMenuItem(
                text = { Text("Nghe ẩn") },
                leadingIcon = { Icon(Icons.Outlined.Headphones, null) },
                onClick = { open = false; acts.listen(v) },
            )
            DropdownMenuItem(
                text = { Text("Tải xuống") },
                leadingIcon = { Icon(Icons.Outlined.Download, null) },
                onClick = { open = false; download = true },
            )
            DropdownMenuItem(
                text = { Text("Chia sẻ") },
                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                onClick = { open = false; shareUrl(ctx, v.url, v.title) },
            )
            if (v.uploaderUrl != null) {
                val actions = LocalActions.current
                DropdownMenuItem(
                    text = { Text("Đến kênh") },
                    leadingIcon = { Icon(Icons.Outlined.Person, null) },
                    onClick = { open = false; actions.openChannel(v.uploaderUrl) },
                )
            }
            extra.forEach { a ->
                DropdownMenuItem(text = { Text(a.label) }, leadingIcon = { Icon(a.icon, null) }, onClick = { open = false; a.onClick() })
            }
        }
    }
    if (addToPlaylist) AddToPlaylistDialog(v) { addToPlaylist = false }
    if (download) vn.zenvid.ui.screens.DownloadDialog(v) { download = false }
}

fun removeAction(label: String, onClick: () -> Unit) = MenuAction(label, Icons.Outlined.Delete, onClick)

@Composable
fun AddToPlaylistDialog(v: VideoItem, onDismiss: () -> Unit) {
    val lib = Services.library
    val scope = rememberCoroutineScope()
    val playlists by lib.playlists.collectAsState(emptyList())
    val containing by remember(v.url) { lib.playlistsContaining(v.url) }.collectAsState(emptyList())
    var creating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lưu video vào…") },
        text = {
            LazyColumn {
                items(playlists, key = { it.playlist.id }) { p ->
                    val checked = p.playlist.id in containing
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            scope.launch { if (checked) lib.removeFromPlaylist(p.playlist.id, v.url) else lib.addToPlaylist(p.playlist.id, v) }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(p.playlist.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Danh sách phát mới")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } },
    )
    if (creating) {
        TextInputDialog("Danh sách phát mới", "Tên", "", confirm = "Tạo", onDismiss = { creating = false }) { name ->
            creating = false
            scope.launch { val id = lib.createPlaylist(name); lib.addToPlaylist(id, v) }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String = "Lưu",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
    )
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (subscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
        contentColor = if (subscribed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    ) {
        Text(
            if (subscribed) "Đã đăng ký" else "Đăng ký",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun ThinProgress() = LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Brand)
