package vn.zenvid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import vn.zenvid.Services
import vn.zenvid.data.Profile
import vn.zenvid.data.Subscription
import vn.zenvid.data.VideoItem
import vn.zenvid.data.YouTube
import vn.zenvid.ui.components.Avatar
import vn.zenvid.ui.components.CenterLoading
import vn.zenvid.ui.components.ErrorView
import vn.zenvid.ui.components.LocalActions
import vn.zenvid.ui.components.MessageView
import vn.zenvid.ui.components.ThinProgress
import vn.zenvid.ui.components.VideoCard

class SubsVm : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var progress by mutableStateOf(0 to 0); private set
    var error by mutableStateOf<String?>(null); private set
    var failed by mutableStateOf(0); private set
    private var lastKey: List<String> = emptyList()
    private var job: Job? = null

    fun load(channels: List<String>, force: Boolean = false) {
        if (!force && channels == lastKey && (loaded || loading)) return
        lastKey = channels
        job?.cancel()
        if (channels.isEmpty()) { items = emptyList(); loaded = true; return }
        loading = true; error = null
        job = viewModelScope.launch {
            try {
                val r = YouTube.subscriptionFeed(channels, force) { d, t -> progress = d to t }
                items = r.items
                failed = r.failed.size
                loaded = true
                if (r.items.isEmpty() && r.failed.isNotEmpty()) error = "Không tải được video từ các kênh. Kiểm tra mạng rồi thử lại."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = YouTube.friendlyError(e)
            } finally {
                loading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(profile: Profile) {
    val vm: SubsVm = viewModel()
    val actions = LocalActions.current
    val subs by Services.library.subscriptions.collectAsState(null)
    val hideShorts by Services.prefs.hideShorts.flow.collectAsState()
    val channels = subs?.map { it.channelUrl }?.sorted()

    LaunchedEffect(channels) { channels?.let { vm.load(it) } }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(profile)
        if (vm.loading && vm.progress.second > 0) ThinProgress()
        val list = subs
        when {
            list == null -> CenterLoading()
            list.isEmpty() -> MessageView(
                Icons.Outlined.Subscriptions,
                title = "Chưa đăng ký kênh nào",
                message = "Bấm \"Đăng ký\" ở trang kênh hoặc dưới video. Bạn cũng có thể nhập danh sách kênh từ YouTube (Google Takeout) trong Cài đặt.",
                actionLabel = "Mở Cài đặt",
                onAction = { actions.openSettings() },
            )
            else -> PullToRefreshBox(
                isRefreshing = vm.loading && vm.loaded,
                onRefresh = { vm.load(channels ?: emptyList(), force = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                val videos = vm.items.filter { !(hideShorts && it.isShort) }
                LazyColumn(Modifier.fillMaxSize()) {
                    item { ChannelStrip(list) }
                    when {
                        !vm.loaded && vm.loading -> item {
                            Text(
                                "Đang tải video mới từ ${vm.progress.first}/${vm.progress.second} kênh…",
                                Modifier.fillMaxWidth().padding(32.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        vm.error != null && videos.isEmpty() -> item { ErrorView(vm.error!!, Modifier.fillMaxWidth().padding(top = 32.dp)) { vm.load(channels ?: emptyList(), true) } }
                        else -> {
                            if (vm.failed > 0) item {
                                Text(
                                    "Không tải được ${vm.failed} kênh – kéo xuống để thử lại.",
                                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            items(videos, key = { it.url }) { VideoCard(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelStrip(subs: List<Subscription>) {
    val actions = LocalActions.current
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(subs, key = { it.channelUrl }) { s ->
                Column(
                    Modifier.width(64.dp).clickable { actions.openChannel(s.channelUrl) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(s.avatar, s.name, 56.dp)
                    Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                }
            }
            item { TextButton(onClick = { actions.openList("channels") }) { Text("Tất cả") } }
        }
    }
}
