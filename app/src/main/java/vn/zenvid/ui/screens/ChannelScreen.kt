package vn.zenvid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.channel.ChannelInfo
import vn.zenvid.Services
import vn.zenvid.data.ChannelTab
import vn.zenvid.data.Entry
import vn.zenvid.data.YouTube
import vn.zenvid.data.bestUrl
import vn.zenvid.data.subscribersText
import vn.zenvid.ui.components.Avatar
import vn.zenvid.ui.components.CenterLoading
import vn.zenvid.ui.components.ErrorView
import vn.zenvid.ui.components.LocalActions
import vn.zenvid.ui.components.OnNearEnd
import vn.zenvid.ui.components.PagedList
import vn.zenvid.ui.components.SmallLoading
import vn.zenvid.ui.components.SubscribeButton
import vn.zenvid.ui.components.shareUrl

class ChannelVm : ViewModel() {
    var info by mutableStateOf<ChannelInfo?>(null); private set
    var tabs by mutableStateOf<List<ChannelTab>>(emptyList()); private set
    var tab by mutableStateOf(0)
    var error by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    private val lists = mutableMapOf<Int, PagedList<Entry>>()
    private var url: String? = null

    fun load(u: String, force: Boolean = false) {
        if (u == url && !force) return
        url = u
        error = null; loading = true
        viewModelScope.launch {
            try {
                val i = YouTube.channel(u)
                info = i
                tabs = YouTube.channelTabs(i)
                lists.clear()
                // Cập nhật ảnh đại diện cho kênh đã đăng ký (kênh nhập từ Takeout chưa có ảnh)
                val lib = Services.library
                val subUrl = YouTube.normalizeChannelUrl(i.url)
                if (subUrl in lib.subscriptionUrls()) lib.setSubscribed(subUrl, i.name, i.avatars.bestUrl(88), true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = YouTube.friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    fun list(index: Int): PagedList<Entry>? {
        val t = tabs.getOrNull(index) ?: return null
        return lists.getOrPut(index) { PagedList(viewModelScope, { it.url }) { YouTube.channelTab(t.handler) } }
    }
}

@Composable
fun ChannelScreen(url: String) {
    val vm: ChannelVm = viewModel(key = "channel_$url")
    val actions = LocalActions.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(url) { vm.load(url) }

    val info = vm.info
    val subscribed by remember(url) { Services.library.isSubscribed(info?.url ?: url) }.collectAsState(false)
    val list = vm.list(vm.tab)
    LaunchedEffect(vm.tab, vm.tabs) { list?.ensureLoaded() }
    val state = rememberLazyListState()
    state.OnNearEnd { list?.loadMore() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Text(info?.name ?: "", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { actions.openSearch(info?.name) }) { Icon(Icons.Outlined.Search, "Tìm kiếm") }
            IconButton(onClick = { shareUrl(ctx, info?.url ?: url, info?.name) }) { Icon(Icons.Outlined.Share, "Chia sẻ") }
        }
        when {
            info == null && vm.loading -> CenterLoading()
            info == null && vm.error != null -> ErrorView(vm.error!!) { vm.load(url, force = true) }
            info != null -> LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                item(key = "header") {
                    Column {
                        info.banners.bestUrl(200)?.let { banner ->
                            AsyncImage(
                                banner, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(6f).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(info.avatars.bestUrl(176), info.name, 72.dp)
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(info.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                                Text(
                                    listOfNotNull(subscribersText(info.subscriberCount)).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        var descOpen by remember { mutableStateOf(false) }
                        if (!info.description.isNullOrBlank()) {
                            Text(
                                info.description, maxLines = if (descOpen) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { descOpen = !descOpen }.padding(horizontal = 12.dp),
                            )
                        }
                        SubscribeButton(
                            subscribed,
                            onClick = { scope.launch { Services.library.setSubscribed(info.url, info.name, info.avatars.bestUrl(88), !subscribed) } },
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        )
                        if (vm.tabs.isNotEmpty()) LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(vm.tabs.size) { i ->
                                FilterChip(
                                    selected = vm.tab == i, onClick = { vm.tab = i }, label = { Text(vm.tabs[i].label) }, border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (list != null) {
                    when {
                        !list.loaded && list.loading -> item { SmallLoading() }
                        list.error != null && list.items.isEmpty() -> item { ErrorView(list.error!!, Modifier.fillMaxWidth()) { list.refresh() } }
                        list.loaded && list.items.isEmpty() -> item {
                            Text("Chưa có nội dung.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            items(list.items, key = { it.url }) { e -> EntryItem(e) }
                            if (list.hasMore) item { SmallLoading() }
                        }
                    }
                } else if (vm.tabs.isEmpty()) {
                    item { Text("Kênh này chưa có nội dung công khai.", Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

