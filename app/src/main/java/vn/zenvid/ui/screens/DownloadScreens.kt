package vn.zenvid.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.zenvid.data.DownloadEntry
import vn.zenvid.data.VideoItem
import vn.zenvid.download.Downloads
import vn.zenvid.download.Kind
import vn.zenvid.download.Status
import vn.zenvid.player.PlayerManager
import vn.zenvid.ui.components.ConfirmDialog
import vn.zenvid.ui.components.LocalActions
import vn.zenvid.ui.components.MessageView
import vn.zenvid.ui.components.Thumbnail
import vn.zenvid.ui.theme.Brand

private fun sizeText(b: Long) = when {
    b <= 0 -> ""
    b < 1_048_576 -> "${b / 1024} KB"
    b < 1_073_741_824 -> String.format(java.util.Locale.US, "%.1f MB", b / 1_048_576.0)
    else -> String.format(java.util.Locale.US, "%.2f GB", b / 1_073_741_824.0)
}

/** Hộp chọn chất lượng khi tải. [available] là các mức chất lượng của video (nếu biết). */
@Composable
fun DownloadDialog(v: VideoItem, available: List<Int> = emptyList(), onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing by remember(v.url) { Downloads.forUrl(v.url) }.collectAsState(emptyList())
    val options = (if (available.isNotEmpty()) available.filter { it >= 144 }.distinct() else listOf(2160, 1080, 720, 480, 360))
        .sortedDescending()

    fun start(kind: String, h: Int) {
        onDismiss()
        scope.launch {
            Downloads.enqueue(v, kind, h)
            (ctx as? vn.zenvid.MainActivity)?.askNotificationPermission()
            Toast.makeText(ctx, "Đã thêm vào danh sách tải. Xem ở Bạn → Video đã tải.", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tải xuống") },
        text = {
            LazyColumn {
                if (existing.isNotEmpty()) item {
                    val done = existing.filter { it.status == Status.DONE }
                    val active = existing.filter { it.status == Status.QUEUED || it.status == Status.RUNNING }
                    Text(
                        listOfNotNull(
                            done.takeIf { it.isNotEmpty() }?.let { "Đã tải: " + it.joinToString { d -> qualityLabel(d) } },
                            active.takeIf { it.isNotEmpty() }?.let { "Đang tải: " + it.size + " bản" },
                        ).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(options) { h ->
                    OptionRow(Icons.Outlined.Videocam, "Video ${h}p" + when { h >= 2160 -> " 4K"; h >= 1440 -> " 2K"; h >= 720 -> " HD"; else -> "" },
                        if (h > 1080) "WebM (VP9)" else "MP4") { start(Kind.VIDEO, h) }
                }
                item { OptionRow(Icons.Outlined.Headphones, "Chỉ âm thanh", "M4A – nghe nhạc, podcast") { start(Kind.AUDIO, 0) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } },
    )
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun qualityLabel(d: DownloadEntry) =
    if (d.kind == Kind.AUDIO) "âm thanh" else "${(d.actualHeight.takeIf { it > 0 } ?: d.height)}p"

/** Màn hình "Video đã tải". */
@Composable
fun DownloadsScreen() {
    val actions = LocalActions.current
    val list by Downloads.all.collectAsState(null)
    var deleting by remember { mutableStateOf<DownloadEntry?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Text("Video đã tải", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
        val items = list
        when {
            items == null -> {}
            items.isEmpty() -> MessageView(
                Icons.Outlined.Download,
                "Bấm \"Tải xuống\" dưới video, hoặc ⋮ → Tải xuống, để lưu video về máy và xem khi không có mạng.",
                title = "Chưa có video nào",
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                val total = items.filter { it.status == Status.DONE }.sumOf { it.totalBytes }
                item {
                    Text(
                        "${items.count { it.status == Status.DONE }} file · ${sizeText(total)} · lưu trong Movies/Zenvid và Music/Zenvid",
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(items, key = { it.id }) { d -> DownloadRow(d, onDelete = { deleting = d }) }
            }
        }
    }

    deleting?.let { d ->
        ConfirmDialog(
            if (d.status == Status.DONE) "Xoá video đã tải?" else "Huỷ tải?",
            if (d.status == Status.DONE) "File \"${d.title}\" sẽ bị xoá khỏi máy." else "Phần đã tải dở sẽ bị xoá.",
            if (d.status == Status.DONE) "Xoá" else "Huỷ tải",
            onDismiss = { deleting = null },
        ) { scope.launch { Downloads.remove(d) } }
    }
}

@Composable
private fun DownloadRow(d: DownloadEntry, onDelete: () -> Unit) {
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val done = d.status == Status.DONE

    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = done) { PlayerManager.openLocal(d); actions.openPlayer() }
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(Modifier.width(150.dp)) {
            Thumbnail(d.toItem(), Modifier.fillMaxWidth(), corner = 8.dp)
            if (d.kind == Kind.AUDIO) Icon(
                Icons.Outlined.Headphones, null, tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(2.dp).size(16.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(d.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(d.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            when (d.status) {
                Status.DONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DownloadDone, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(" " + qualityLabel(d) + " · " + sizeText(d.totalBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Status.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Text(" " + (d.error ?: "Lỗi"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
                else -> Column {
                    Spacer(Modifier.height(6.dp))
                    if (d.status == Status.RUNNING && d.totalBytes > 0) {
                        LinearProgressIndicator(progress = { (d.downloadedBytes.toFloat() / d.totalBytes).coerceIn(0f, 1f) }, color = Brand, modifier = Modifier.fillMaxWidth())
                        Text("${sizeText(d.downloadedBytes)} / ${sizeText(d.totalBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LinearProgressIndicator(color = Brand, modifier = Modifier.fillMaxWidth())
                        Text(if (d.status == Status.QUEUED) "Đang chờ…" else "Đang chuẩn bị…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Tuỳ chọn", Modifier.size(20.dp)) }
            DropdownMenu(menu, { menu = false }) {
                if (d.status == Status.FAILED) DropdownMenuItem({ Text("Thử lại") }, { menu = false; scope.launch { Downloads.retry(d) } }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) })
                if (done && d.kind == Kind.VIDEO) DropdownMenuItem({ Text("Nghe ẩn") }, {
                    menu = false
                    PlayerManager.openLocal(d, audioOnly = true)
                }, leadingIcon = { Icon(Icons.Outlined.Headphones, null) })
                if (done) DropdownMenuItem({ Text("Mở bằng app khác") }, {
                    menu = false
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(d.fileUri), d.mime ?: "video/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        )
                    }.onFailure { Toast.makeText(ctx, "Không có app nào mở được file này", Toast.LENGTH_SHORT).show() }
                }, leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) })
                DropdownMenuItem({ Text("Xem bản trực tuyến") }, { menu = false; actions.openVideo(d.toItem()) }, leadingIcon = { Icon(Icons.Outlined.Videocam, null) })
                DropdownMenuItem({ Text(if (done) "Xoá khỏi máy" else "Huỷ tải") }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
            }
        }
    }
}

/** Phần dưới trình phát khi đang xem file đã tải. */
@Composable
fun LocalDetails(s: PlayerManager.State.Local) {
    val actions = LocalActions.current
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text(s.video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(s.video.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DownloadDone, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            Text("  Đang phát bản đã tải (${qualityLabel(s.download)}) – không cần mạng", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = { actions.openList("downloads") }) { Text("Video đã tải khác") }
            TextButton(onClick = {
                PlayerManager.close()
                actions.openVideo(s.video)
            }) { Text("Xem bản trực tuyến") }
        }
    }
}
