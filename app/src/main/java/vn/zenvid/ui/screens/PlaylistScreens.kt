package vn.zenvid.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import vn.zenvid.Services
import vn.zenvid.data.Entry
import vn.zenvid.data.PageResult
import vn.zenvid.data.VideoItem
import vn.zenvid.data.YouTube
import vn.zenvid.data.bestUrl
import vn.zenvid.data.toVideo
import vn.zenvid.ui.components.CenterLoading
import vn.zenvid.ui.components.ConfirmDialog
import vn.zenvid.ui.components.ErrorView
import vn.zenvid.ui.components.LocalActions
import vn.zenvid.ui.components.MessageView
import vn.zenvid.ui.components.OnNearEnd
import vn.zenvid.ui.components.PagedList
import vn.zenvid.ui.components.SmallLoading
import vn.zenvid.ui.components.TextInputDialog
import vn.zenvid.ui.components.VideoRow
import vn.zenvid.ui.components.removeAction
import vn.zenvid.ui.components.shareUrl

class PlaylistVm : ViewModel() {
    var info by mutableStateOf<PlaylistInfo?>(null); private set
    var list: PagedList<Entry>? by mutableStateOf(null); private set
    private var url: String? = null

    fun load(u: String) {
        if (u == url) return
        url = u
        list = PagedList(viewModelScope, { it.url }) {
            val (i, more) = YouTube.playlist(u)
            info = i
            PageResult<Entry>(i.relatedItems.map { it.toVideo() }, i.nextPage.takeIf { i.hasNextPage() }) to more
        }.also { it.refresh() }
    }
}

@Composable
private fun ListHeader(
    title: String,
    subtitle: String,
    thumbnail: String?,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
) {
    Column(Modifier.padding(12.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) AsyncImage(thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            else Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, Modifier.width(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onPlay?.invoke() }, enabled = onPlay != null, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Icon(Icons.Filled.PlayArrow, null); Text(" Phát tất cả") }
            FilledTonalButton(onClick = { onShuffle?.invoke() }, enabled = onShuffle != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Shuffle, null); Text(" Trộn bài")
            }
        }
    }
}

/** Danh sách phát trên YouTube. */
@Composable
fun PlaylistScreen(url: String) {
    val vm: PlaylistVm = viewModel(key = "pl_$url")
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(url) { vm.load(url) }
    val list = vm.list ?: return
    val info = vm.info
    val state = rememberLazyListState()
    state.OnNearEnd { list.loadMore() }
    val videos = list.items.filterIsInstance<VideoItem>()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Text(info?.name ?: "", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(enabled = videos.isNotEmpty(), onClick = {
                scope.launch {
                    Services.library.copyToNewPlaylist(info?.name ?: "Danh sách phát", videos)
                    Toast.makeText(ctx, "Đã lưu ${videos.size} video vào Thư viện", Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Outlined.LibraryAdd, "Lưu vào thư viện") }
            IconButton(onClick = { shareUrl(ctx, url, info?.name) }) { Icon(Icons.Outlined.Share, "Chia sẻ") }
        }
        when {
            !list.loaded && list.loading -> CenterLoading()
            list.error != null && list.items.isEmpty() -> ErrorView(list.error!!) { list.refresh() }
            else -> LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                item {
                    ListHeader(
                        info?.name ?: "",
                        listOfNotNull(info?.uploaderName, info?.streamCount?.takeIf { it >= 0 }?.let { "$it video" }).joinToString(" · "),
                        info?.thumbnails.bestUrl(480),
                        onPlay = videos.firstOrNull()?.let { first -> { actions.openVideo(first, videos) } },
                        onShuffle = videos.takeIf { it.isNotEmpty() }?.let { v -> { val s = v.shuffled(); actions.openVideo(s.first(), s) } },
                    )
                }
                items(videos, key = { it.url }) { VideoRow(it, queue = videos) }
                if (list.hasMore) item { SmallLoading() }
            }
        }
    }
}

/** Danh sách phát do bạn tạo (lưu trên máy). */
@Composable
fun LocalPlaylistScreen(id: Long) {
    val lib = Services.library
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val playlist by remember(id) { lib.playlist(id) }.collectAsState(null)
    val items by remember(id) { lib.playlistItems(id) }.collectAsState(null)
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val videos = items?.map { it.toItem() } ?: emptyList()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Text(playlist?.name ?: "", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Tuỳ chọn") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Đổi tên") }, { menu = false; renaming = true }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                    DropdownMenuItem({ Text("Xoá danh sách") }, { menu = false; deleting = true }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                }
            }
        }
        when {
            items == null -> CenterLoading()
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    ListHeader(
                        playlist?.name ?: "",
                        "${videos.size} video · Chỉ trên máy này",
                        videos.firstOrNull()?.thumbnail,
                        onPlay = videos.firstOrNull()?.let { first -> { actions.openVideo(first, videos) } },
                        onShuffle = videos.takeIf { it.isNotEmpty() }?.let { v -> { val s = v.shuffled(); actions.openVideo(s.first(), s) } },
                    )
                }
                if (videos.isEmpty()) item {
                    MessageView(Icons.AutoMirrored.Filled.PlaylistPlay, "Chưa có video. Bấm ⋮ ở một video rồi chọn \"Lưu vào danh sách phát\".", Modifier.fillMaxWidth())
                }
                items(videos, key = { it.url }) { v ->
                    VideoRow(v, queue = videos, extraMenu = listOf(removeAction("Xoá khỏi danh sách này") { scope.launch { lib.removeFromPlaylist(id, v.url) } }))
                }
            }
        }
    }

    if (renaming) TextInputDialog("Đổi tên danh sách", "Tên", playlist?.name ?: "", onDismiss = { renaming = false }) { name ->
        renaming = false; scope.launch { lib.renamePlaylist(id, name) }
    }
    if (deleting) ConfirmDialog(
        "Xoá danh sách phát?",
        "\"${playlist?.name}\" sẽ bị xoá vĩnh viễn.",
        "Xoá", onDismiss = { deleting = false }) {
        scope.launch { lib.deletePlaylist(id); actions.back() }
    }
}
